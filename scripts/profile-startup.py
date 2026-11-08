"""Opt-in Docker startup comparison. Only uses an explicitly isolated CI database.

Both modes use the same image, 0.1 CPU and a hard 512 MiB memory limit. Never
connects to a production URL or updates platform configuration. Raw logs are local.
"""
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path


def command(*args, check=True):
    return subprocess.run(args, text=True, capture_output=True, check=check).stdout.strip()


def main():
    if os.environ.get('CI') != 'true' or os.environ.get('DB_URL') != 'jdbc:postgresql://localhost:5432/caizhitong_startup_perf_test':
        raise SystemExit('Profiling requires the isolated local CI startup database.')
    image = 'caizhitong-backend:ci'
    config = json.loads(command('docker', 'image', 'inspect', image))[0]['Config']['Env']
    candidate = next(item.split('=', 1)[1] for item in config if item.startswith('JAVA_TOOL_OPTIONS='))
    if '-XX:TieredStopAtLevel=1' not in candidate:
        raise SystemExit('Candidate image must contain the measured Tier 1 setting.')
    baseline = candidate.replace('-XX:TieredStopAtLevel=1', '')
    folder = Path('.runtime/startup-ci')
    folder.mkdir(parents=True, exist_ok=True)
    results = []
    for index, mode in enumerate(['warmup', 'baseline', 'tier1', 'tier1', 'baseline']):
        name = f'caizhitong-startup-{index}'
        started = time.monotonic()
        args = ['docker', 'run', '-d', '--name', name, '--network', 'host', '--cpus', '0.1', '--memory', '512m', '--memory-swap', '512m']
        for key in ['DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET']:
            args += ['-e', key]
        args += ['-e', f'JAVA_TOOL_OPTIONS={baseline if mode == "baseline" else candidate}',
                 '-e', 'SERVER_PORT=18081', '-e', 'DEMO_ENABLED=false', '-e', 'BOOTSTRAP_ADMIN_ENABLED=false',
                 '-e', 'STORAGE_MODE=local', '-e', 'ALERT_SCAN_ENABLED=false', '-e', 'MAIL_DISPATCH_ENABLED=false',
                 '-e', 'GITHUB_OAUTH_ENABLED=true', '-e', 'GITHUB_CLIENT_ID=synthetic-startup-client',
                 '-e', 'GITHUB_CLIENT_SECRET=synthetic-startup-secret',
                 '-e', 'GITHUB_REDIRECT_URI=https://backend.example.test/login/oauth2/code/github',
                 '-e', 'PUBLIC_WEB_URL=https://frontend.example.test', image]
        try:
            command(*args)
            deadline = started + 300
            ready = None
            while time.monotonic() < deadline:
                state = json.loads(command('docker', 'inspect', name))[0]['State']
                if not state['Running']:
                    raise RuntimeError(f'{mode} container exited before readiness')
                try:
                    with urllib.request.urlopen('http://127.0.0.1:18081/actuator/health', timeout=1) as response:
                        healthy = json.load(response).get('status') == 'UP'
                    logs = command('docker', 'logs', name)
                    if healthy and 'Application ready: startupMs=' in logs:
                        ready = time.monotonic() - started
                        break
                except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError):
                    pass
                time.sleep(1)
            if ready is None:
                raise RuntimeError(f'{mode} readiness exceeded 300 seconds')
            with urllib.request.urlopen('http://127.0.0.1:18081/api/auth/oauth/config', timeout=3) as response:
                assert json.load(response)['data']['githubEnabled'] is True
            spring = re.search(r'Started CaiZhiTongApplication in ([\d.]+) seconds', logs)
            diagnostics = re.search(r'Application ready: startupMs=(\d+), jvmUptimeMs=(\d+), compilationMs=(\d+), heapUsedMiB=(\d+)', logs)
            state = json.loads(command('docker', 'inspect', name))[0]
            assert not state['State']['OOMKilled'] and state['RestartCount'] == 0
            peak = command('docker', 'exec', name, 'cat', '/sys/fs/cgroup/memory.peak', check=False)
            result = dict(index=index, mode=mode, readySeconds=round(ready, 3), springSeconds=float(spring[1]),
                          startupMs=int(diagnostics[1]), jvmUptimeMs=int(diagnostics[2]), compilationMs=int(diagnostics[3]),
                          heapUsedMiB=int(diagnostics[4]), peakMemoryMiB=round(int(peak) / 1048576, 2) if peak.isdigit() else None,
                          memoryLimitMiB=512, cpuQuota=0.1, oomKilled=False, restartCount=0)
            results.append(result)
            print(json.dumps(result), flush=True)
        finally:
            logs = subprocess.run(['docker', 'logs', name], capture_output=True, text=True)
            # CI-only synthetic data; do not upload unfiltered production logs.
            (folder / f'{index}-{mode}.log').write_text(logs.stdout + logs.stderr, encoding='utf-8')
            command('docker', 'rm', '-f', name, check=False)
            (folder / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    baseline_times = [row['startupMs'] for row in results if row['mode'] == 'baseline']
    candidate_times = [row['startupMs'] for row in results if row['mode'] == 'tier1']
    print(json.dumps({'meanBaselineMs': sum(baseline_times) / len(baseline_times), 'meanTier1Ms': sum(candidate_times) / len(candidate_times)}))
    # Require evidence of improvement without an arbitrary absolute startup SLA.
    assert sum(candidate_times) < sum(baseline_times), 'Candidate did not improve the repeated same-runner startup measurement'


if __name__ == '__main__':
    main()
