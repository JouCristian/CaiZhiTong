# Production stability · 2026-11-08

## Scope and status

This is a maintenance exercise, not a new feature release. The frozen `v0.7.0` tag remains unchanged. No production user, password, permission, attachment, historical migration, database or hosting plan is replaced. Production business writes are not used as availability tests.

[PR #4](https://github.com/JouCristian/CaiZhiTong/pull/4) was merged at 2026-11-08T10:56:47Z as `48fefa24a330a7d274a48b5d7411c5d5ad3363a6`, after checking the exact validated head `8a3b7b0` and successful push/PR CI. Render deployment `dep-dbo5asavcj2c73908iig` became live at 10:59:30Z; the Vercel **production** deployment `dpl_8FCVv1AvQtb12yboSWQk8MhVjdkL` is READY at the same commit. The production custom alias remains <https://caizhitong-eight.vercel.app>.

Post-deployment checks: health 200/UP (1.217 s), OAuth config 200/enabled (2.608 s), exact production CORS, desktop and mobile GitHub entry enabled. No real GitHub account authorization was automated. Frontend timeout/recovery logic is live; this alone does not prove reliable backend spin-up.

Earlier incident evidence and the distinction between restart/redeploy and idle spin-up are preserved in [FIX-LOGIN-CONNECTIVITY.md](FIX-LOGIN-CONNECTIVITY.md).

## Measured startup hypothesis

The earlier Render startup took 118.595 s (JVM uptime 127.687 s). Flyway validation itself took 1.066 s and Hikari initialization about 2.6 s; the logs do not justify disabling migrations, changing credentials, relaxing private storage validation or moving the database. The dominant unexplained time occurs during JVM/context initialization.

An isolated PostgreSQL database, `caizhitong_startup_perf_test`, was used with the existing application JAR, matching memory settings, one Windows CPU affinity, and `-XX:MaxRAM=512m`. There was one untimed database warmup followed by interleaved runs:

| Mode | First health UP (s), 3 runs | JVM compiler time (s), 3 runs | Peak process working set (MiB) |
| --- | --- | --- | --- |
| Existing tiered compiler | 16.208 / 19.508 / 21.118 | 11.18 / 13.60 / 14.64 | 230.84 / 227.76 / 228.20 |
| `-XX:TieredStopAtLevel=1` | 6.218 / 6.269 / 8.659 | 0.67 / 0.70 / 0.96 | 180.38 / 180.64 / 181.38 |

These are local paired measurements, **not Render timings or a hard 512 MiB process limit**. They support reducing optimizing-compiler CPU work on a constrained demo service. Tier 1 can reduce steady-state peak throughput, so it is a tradeoff for a low-traffic, frequently restarted demo, not a universal JVM recommendation.

The candidate adds only that JVM option to the image and a credential-free `ApplicationReadyEvent` diagnostic (after synchronous runners). It retains eager initialization, Flyway validation/migration, private bucket verification, Demo isolation, existing pools, GC and memory limits. No bean or security check is made lazy.

`scripts/profile-startup.py` runs the **same image** with and without the flag, interleaved after a warmup, at a Docker-enforced **0.1 CPU / 512 MiB** limit. It requires a CI-only, loopback database, records ready/log/heap/compiler/cgroup peak memory data, checks OOM/restart state and retains isolated artifacts. This models a quota; it cannot reproduce Render scheduling or network conditions. CI and production results are recorded below only after completion.

## Genuine idle-spin-up protocol

1. Record the exact deployed commit and the last successful public response. Close diagnostic browsers and stop public checks.
2. Leave the public application untouched for at least 17 minutes. Render management API state/log reads do not request the application. Check management evidence at sparse checkpoints; do not send periodic application requests during this interval.
3. Confirm a real shutdown/empty instance list; elapsed time alone is insufficient. If an unrelated visitor prevents sleep, record that and extend the idle interval rather than claiming a cold start.
4. Trigger a single bounded request from the local network and a manual GitHub-hosted runner, recording request start, TLS completion, first response byte, status and total time. Keep failures as evidence. No restart or deployment during the observation window.
5. Correlate Render Events and new instance/startup/Flyway/Hikari/ready logs. Then confirm the OAuth configuration is available.
6. Repeat from a fresh idle interval. Two deployment starts are not two idle-spin-up tests. If a cycle fails, record it as failed; do not redeploy repeatedly to manufacture a pass.

Times in machine-readable artifacts are UTC; user-facing reports also give Asia/Shanghai. Application request timestamps include local DNS/TLS/routing and are not identical to Spring startup duration.

## Credential-free availability checks

Run `node scripts/check-availability.mjs`, or manually dispatch **Production availability (on demand)** in GitHub Actions. It issues exactly one GET to each of `/actuator/health` and `/api/auth/oauth/config`. No login, JWT or business API access is needed. Each request has a configurable **total deadline**, including body reception (default 180 s; allowed 1–240 s).

The JSONL output includes HTTP status, TLS/first-byte/total timing, bounded outcome categories, and only the public OAuth-enabled boolean. Response bodies, headers, credentials and arbitrary error messages are not logged. HTTP failures, invalid JSON, network failures and timeouts fail the command/job; artifacts are retained even on failure for 30 days. OAuth disabled is a valid response, reported separately as `githubEnabled=false`, not confused with a connectivity failure. GitHub CI and Render deploy status supplement HTTP checks for deployment failures.

The workflow has **no schedule** and does not keep the service awake. After testing, any periodic check requires an explicit operating choice: a short interval can suppress ordinary idle sleep and consume free instance hours; an interval above 15 minutes measures cold starts. Neither prevents platform scheduling faults. No background write-capable monitoring account is created.

Tests: `node --test scripts/check-availability.test.mjs` uses loopback synthetic responses only, covering healthy JSON, deadlines, disabled OAuth, invalid response, HTTP failure, connection failure, oversized responses and rejection of credential-bearing URLs.

## Results and limits

Production idle cycles, constrained Docker measurements, final CI and the hosting recommendation will be appended after real execution. Until then they are **pending**, not passed. No scheduled ping, paid upgrade, migration or production credential change has been made.

## Recovery boundaries

If the instance list is empty, preserve management state, logs, events and bounded network measurements before taking action. Do not delete tables, repair Flyway or rotate unrelated credentials. Frontend retry improves the visitor experience but cannot repair an unscheduled backend. A platform support report is maintained in [RENDER-SUPPORT-REPORT.md](RENDER-SUPPORT-REPORT.md); it is prepared for the owner and is not automatically sent.
