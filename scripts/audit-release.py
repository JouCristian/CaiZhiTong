"""Read-only release audit. Never prints matched secret values or blob content."""
from pathlib import Path
import os
import re
import subprocess
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]

def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE).stdout

def sensitive_path(path):
    parts = path.replace('\\', '/').split('/')
    name = parts[-1].lower()
    return (any(p in {'.runtime', '.tools', 'node_modules', 'target', 'dist', 'pgdata',
                      'uploads', 'test-results', 'playwright-report'} for p in parts)
            or (name.startswith('.env') and name not in {'.env.example', '.env.cloud.example'})
            or name.endswith(('.log', '.pem', '.key', '.p12', '.pfx', '.jks',
                              '.keystore', '.dump', '.backup')))

secrets = []
for envfile in list(ROOT.glob('.env*')) + list((ROOT / 'frontend').glob('.env*')):
    if envfile.name.endswith('.example'):
        continue
    if envfile.exists():
        for line in envfile.read_text(encoding='utf-8-sig').splitlines():
            key, sep, value = line.partition('=')
            if sep and re.search(r'PASSWORD|SECRET|TOKEN|CREDENTIAL|_KEY', key) and len(value) >= 8:
                if not value.startswith('REPLACE_'):
                    secrets.append(value.encode('utf-8'))
for key, value in os.environ.items():
    if key in {'GH_TOKEN', 'GITHUB_TOKEN', 'GH_ENTERPRISE_TOKEN'} and len(value) >= 8:
        secrets.append(value.encode())

patterns = [
    rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
    rb'gh[pousr]_[A-Za-z0-9]{30,}',
    rb'github_pat_[A-Za-z0-9_]{40,}',
    rb'AKIA[0-9A-Z]{16}',
    rb'npm_[A-Za-z0-9]{36}',
    rb'sk-(?:proj-|svcacct-)[A-Za-z0-9_-]{40,}',
    rb'xox[baprs]-[A-Za-z0-9-]{20,}',
    rb'sb_secret_[A-Za-z0-9_-]{24,}',
    rb'eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{20,}',
]
def has_secret(data):
    return any(s in data for s in secrets) or any(re.search(p, data) for p in patterns)

problems = []
files = set(p.decode('utf-8') for p in git('ls-files', '-z', '--cached', '--others',
                                        '--exclude-standard').split(b'\0') if p)
tracked = [p.decode('utf-8') for p in git('ls-files', '-z').split(b'\0') if p]
for path in tracked:
    if sensitive_path(path):
        problems.append('Tracked sensitive/generated path: ' + path)
for path in sorted(files):
    if sensitive_path(path):
        problems.append('Unsafe release candidate: ' + path)
    source = ROOT / path
    if source.is_file() and has_secret(source.read_bytes()):
        problems.append('Possible secret in candidate (value suppressed): ' + path)
    if source.suffix.lower() == '.md' and source.is_file():
        content = source.read_text(encoding='utf-8-sig')
        for href in re.findall(r'\]\(([^)]+)\)', content):
            href = href.strip('<>').split('#')[0]
            if not href or re.match(r'^(?:https?://|mailto:)', href):
                continue
            if re.match(r'^[A-Za-z]:[/\\]|^file:', href):
                problems.append('Machine-specific document link: ' + path)
            elif not (source.parent / unquote(href)).exists():
                problems.append('Broken local document link: ' + path + ' -> ' + href)
        for href in re.findall(r'<img\b[^>]*\bsrc=["\']([^"\']+)["\']', content):
            if not href.startswith(('https://', 'http://')) and not (source.parent / href).is_file():
                problems.append('Broken README image: ' + path + ' -> ' + href)

# Include dangling objects as well as reachable history, without changing refs
# or exposing their contents. Only reachable main history will ever be pushed.
objects = git('cat-file', '--batch-all-objects', '--batch-check=%(objectname) %(objecttype)').splitlines()
blob_ids = [row.split()[0] for row in objects if row.endswith(b' blob')]
commit_count = sum(row.endswith(b' commit') for row in objects)
if blob_ids:
    batch = subprocess.run(['git', 'cat-file', '--batch'], cwd=ROOT,
                           input=b'\n'.join(blob_ids) + b'\n', check=True,
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    offset = 0
    for oid in blob_ids:
        end = batch.index(b'\n', offset)
        size = int(batch[offset:end].split()[-1])
        body = batch[end + 1:end + 1 + size]
        offset = end + size + 2
        if has_secret(body):
            problems.append('Possible secret in Git blob (value suppressed): ' + oid.decode())

if problems:
    print('\n'.join('FAIL ' + p for p in problems))
    sys.exit(1)
print(f'PASS: {len(files)} candidate files, {len(tracked)} tracked paths, '
      f'{len(blob_ids)} Git blobs (including unreachable), {commit_count} commit objects; '
      'no detected local secrets/token signatures, unsafe paths or broken Markdown links.')
print('This audit supplements manual review; it does not certify absence of all unknown secrets.')
