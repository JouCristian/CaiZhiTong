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

`scripts/profile-startup.py` runs the **same image** with and without the flag, interleaved after a warmup, at a Docker-enforced **0.1 CPU / 512 MiB** limit. It requires a CI-only, loopback database, records ready/log/heap/compiler/cgroup peak memory data, checks OOM/restart state and retains isolated artifacts. This models a quota; it cannot reproduce Render scheduling or network conditions. It ran on the initial candidate push/PR/main checks. The verification follow-up makes this approximately ten-minute comparison **opt-in via manual dispatch of Build and test**, while normal push/PR CI retains all original regression tests, probe tests, image build and 512 MiB runtime smoke. Run `gh workflow run ci.yml --ref main` to repeat the full quota experiment; it never probes production.

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

The JSONL output includes HTTP status, TLS/first-byte/total timing, bounded outcome categories, and only the public OAuth-enabled boolean. The verification follow-up measures the first plaintext socket data before header parsing completes, tested with deliberately delayed headers. Earlier warm samples measured response-header arrival; all failed-cycle samples received no bytes, so their null first-byte result is unaffected. Response bodies, headers, credentials and arbitrary error messages are not logged. HTTP failures, invalid JSON, network failures and timeouts fail the command/job; artifacts are retained even on failure for 30 days. OAuth disabled is a valid response, reported separately as `githubEnabled=false`, not confused with a connectivity failure. GitHub CI and Render deploy status supplement HTTP checks for deployment failures.

The workflow has **no schedule** and does not keep the service awake. After testing, any periodic check requires an explicit operating choice: a short interval can suppress ordinary idle sleep and consume free instance hours; an interval above 15 minutes measures cold starts. Neither prevents platform scheduling faults. No background write-capable monitoring account is created.

Tests: `node --test scripts/check-availability.test.mjs` uses loopback synthetic responses only, covering healthy JSON, deadlines, disabled OAuth, invalid response, HTTP failure, connection failure, oversized responses and rejection of credential-bearing URLs.

## Results and limits

### Constrained Docker evidence

[Candidate CI 42474251299](https://github.com/JouCristian/CaiZhiTong/actions/runs/42474251299) passed. After one untimed warmup, two runs per mode on the same runner/image, 0.1 CPU / 512 MiB:

| Mode | Spring context (s) | Application ready after runners (s) | First observed health + ready (s) | cgroup peak memory (MiB) |
| --- | --- | --- | --- | --- |
| Baseline 1 | 151.098 | 156.998 | 174.386 | 198.17 |
| Tier 1 run 1 | 77.401 | 80.395 | 89.764 | 156.97 |
| Tier 1 run 2 | 75.505 | 79.103 | 88.070 | 156.71 |
| Baseline 2 | 156.798 | 164.399 | 179.321 | 199.79 |

Mean application ready: **160.699 → 79.749 s (50.4% shorter)**. Every run stayed within the hard memory limit, `OOMKilled=false`, restart count 0. These are isolated quota measurements, not production promises. JVM compiler monitoring time is cumulative elapsed compilation time, not a direct per-process CPU-time measurement.

The same CI passed Maven 195/195, TypeScript/build, Mock OAuth/Demo/connection 34/34, motion 6/6, probe tests 5/5, image build and the original 512 MiB runtime security smoke.

### Idle observation excluded from cold-start counts

After the last local normal checks at about 11:01Z, an explicitly delayed GitHub probe at **11:20:00Z** returned health 200 in 0.432 s (TLS 0.041 s) and config 200 in 0.292 s (TLS 0.016 s). Later log correlation shows that the running service did **not** shut down until **11:46:48Z**. Thus the 11:20 request is a **warm availability check**, not a passed idle-spin-up cycle. Uncontrolled public traffic can postpone sleep; simply waiting 17 minutes is insufficient. The one-time diagnostic push trigger was removed from the final workflow.

### Genuine idle cycle 1 — failed before optimization deployment

Production commit: `48fefa2` (PR #4). Last observed normal response: **11:20:00.784Z** (independent runner config). The application subsequently shut down at **11:46:48Z**. Management API returned an empty running-instance list at about **13:26:44Z**, before the first wake request. No restart or deployment intervened.

| Path / network | Request start UTC | TLS complete (s) | First byte | Final result / elapsed |
| --- | --- | --- | --- | --- |
| health / local | 13:28:04.850 | 0.713 | none | timeout / 240.057 s |
| health / GitHub Ubuntu | 13:28:13.770 | 0.081 | none | timeout / 240.014 s |
| OAuth config / local | 13:32:04.919 | 0.624 | none | timeout / 240.008 s |
| OAuth config / GitHub Ubuntu | 13:32:13.786 | 0.030 | none | timeout / 240.002 s |

Independent failure and retained artifacts: [Actions 42485090406](https://github.com/JouCristian/CaiZhiTong/actions/runs/42485090406). This job failed intentionally on the real timeout; it is not a regression-test failure or a passed wake test. At **13:32:53Z** and **13:43:42Z**, service state was still Free/not_suspended/desired=1 with **zero instances**. Event history had no new deployment/restart. A bounded log read at **13:41Z** still ended with the 11:46:48Z shutdown. Some intermediate management log requests timed out and are recorded as unavailable rather than fabricated empty logs.

New instance creation, Spring startup completion, Flyway/Hikari phase timings and OAuth availability are **not available in this cycle because no new startup was observed**. Sequential config requests are part of the same failed cycle, not an additional independent cycle.

At 13:39Z the actual production frontend was checked at 1440 px and 390 px during this outage: no endless spinner after the 8 s probe deadline, visible reconnect, editable password input retained across reconnect, two synchronous reconnect clicks produced one request, no horizontal overflow. No real login was submitted. This confirms the deployed UX recovery behavior while the backend remained unavailable; it does not make password or OAuth authentication succeed without a backend.

### Optimization production deployment — not an idle-spin-up pass

[PR #5](https://github.com/JouCristian/CaiZhiTong/pull/5), exact validated head `12071f3`, was merged at **13:51:10Z** as `585a941b90972d6f3af637e2a922cfb6b198f1df`. [Push CI 42485386504](https://github.com/JouCristian/CaiZhiTong/actions/runs/42485386504) and [PR CI 42485390156](https://github.com/JouCristian/CaiZhiTong/actions/runs/42485390156) both passed before merge. The final workflow has only a manual dispatch trigger. The production deployment was to deliver the measured JVM change, not a same-version redeploy counted as a passed wake.

Render `dep-dbo7sk0ae00c739d8aeg` started 13:51:13Z and became live **13:53:15.764Z**. Vercel production deployment `dpl_E4heHgmtcu5bw9Mq8xNDAKd7MzmA` is READY at the same commit. The Render platform has no JAVA_TOOL_OPTIONS override; container logs confirm the image's Tier 1 flag was picked up.

| Production phase | Actual UTC / measurement |
| --- | --- |
| JVM invocation | 13:51:59.387Z |
| Spring starting log | 13:52:09.422Z |
| Flyway validation | 13:52:38.098Z; **0.433 s** |
| Schema | V7, up to date, no new migration |
| Spring context started | 13:53:04.317Z; **59.794 s**, JVM uptime 64.930 s |
| Hikari | 13:53:04.618–13:53:06.117Z; about **1.5 s** |
| Application ready after runners | 13:53:10.072Z; **63.918 s**, JVM uptime 70.685 s |
| Ready diagnostics | cumulative compilation 18.829 s; heap in use 43 MiB |
| Observed container memory | max **163.48 MiB** in six one-minute Render samples, not an instantaneous peak / load test |
| Public health | 13:55:13Z, 200/UP, 4.691 s total including 4.022 s TLS/network |
| Public OAuth config | 13:55:14Z, 200/enabled, 1.278 s total |

Spring context startup is about **48% shorter** than the immediately preceding production start (114.398 s), and about half of the earlier 118.595 s recovery start. These are separate production deployments under shared resources, not a perfectly controlled experiment; the Docker paired test supplies the controlled comparison. Context-to-ready was about 4.1 s, so synchronous runners are a small measured part of this startup. No required initialization was disabled. No new OOM/restart was observed in the deployment window; this is not a long-running stress test.

Production desktop/mobile checks returned config with the exact Vercel CORS origin and enabled the GitHub entry, without spinner or horizontal overflow. On the local browser path desktop took 19.9 s (including recovery from a slow request), mobile 7.0 s; this records real path variability instead of promising sub-second login. No real GitHub authorization or password submission was performed. The last normal observation is conservatively **13:55:46Z**; application requests stop from then until the next confirmed sleep.

### Local candidate regression

Using the candidate Tier 1 option, local Maven **195/195** passed against the dedicated `caizhitong_stability_test` database (10:02 min). OAuth/Demo/connection desktop/mobile **34/34** (1.7 min), original business desktop/mobile **70/70** (7.6 min, including the six motion cases), five API smoke groups, TypeScript and production build passed. The normal Ant Design chunk-size warning remains.

Earlier local attempts did not pass: an execution interruption stopped the first Maven run, Windows PowerShell 5 treated a native build warning as an error, and local PostgreSQL required automatic recovery after the interruption. Tests were rerun using PowerShell 7 after PostgreSQL reported ready; no database was deleted, no WAL file was removed and no Flyway repair was used. Candidate production credentials were never used for test fixtures. Generated historical screenshots/examples were restored; two new outage screenshots are explicitly from the real production frontend with synthetic input and no submitted login: [desktop](screenshots/stability-production-unreachable-desktop.png), [mobile](screenshots/stability-production-unreachable-mobile.png).

### Genuine idle cycle 2 — failed after optimization deployment

Production commit remained **585a941**. The final normal browser verification finished at about **13:55:45.621Z**; the idle clock conservatively starts at **13:55:46Z**. No application requests were sent during the idle window. Management reads do not target the backend application.

- **14:10:44.130Z**: graceful shutdown begins; Hikari shutdown completes **14:10:44.345Z**.
- **14:11:36.821Z**: running-instance list is empty, Free/not_suspended/desired=1.
- **14:15:08.131Z**: control script confirms both empty instances and this new shutdown log (later than the last normal response). It dispatches the independent runner and then the local probe. There was no intervening restart, redeploy or configuration change.

| Path / network | Request start UTC | TLS complete (s) | First byte | Final result / elapsed |
| --- | --- | --- | --- | --- |
| health / local | 14:15:16.341 | 0.862 | none | timeout / 240.091 s |
| health / GitHub Ubuntu | 14:15:25.760 | 0.042 | none | timeout / 240.015 s |
| OAuth config / local | 14:19:16.441 | 0.991 | none | timeout / 240.011 s |
| OAuth config / GitHub Ubuntu | 14:19:25.776 | 0.041 | none | timeout / 240.002 s |

[Independent run 42488891370](https://github.com/JouCristian/CaiZhiTong/actions/runs/42488891370) correctly failed and retained its JSONL artifact. At **14:19:58Z** the service remained Free/not_suspended/desired=1/zero instances; at **14:23:22Z** the instance list was still empty. A **14:23:20Z** bounded log query still ended at the 14:10:44Z shutdown. No new Java start, Flyway, Hikari or application-ready phase exists to time in this failed wake. Those fields are **not observed**, not zero-second successful phases. Events/log snapshots and [whitelisted machine-readable evidence](evidence/production-stability-2026-11-08.json) retain both failed cycles after transient Actions artifacts expire.

This is a second, independent **real idle/shutdown/request** cycle, now on the optimized image. It establishes that the measured Java startup improvement did **not** resolve this service's observed failure to obtain a new running instance. It does not prove which internal Render subsystem failed. A separate ordinary-root browser navigation is kept outside these two cycle measurements.

Additional browser observation (not a third cycle): a fresh headless Chrome navigated to the backend `/` at **14:24:45Z**, with a 240 s upper bound and no credentials. It ended in a navigation error after **102.948 s**, without an HTTP status. Browser timing details were unavailable (-1), so this is **not** reported as a measured TLS success or 240 s timeout. It does not replace the complete independent-network timing evidence above. There was no production login submission or data mutation.

## Outcome and hosting decision

**Application recovery UX and startup optimization passed; production idle-spin-up reliability did not pass.** Two genuine wake cycles failed (before and after optimization), in addition to the earlier documented incident. A successful deploy is not counted as a wake success. No production business data, historical migration, permission, password or private attachment was changed. No scheduled ping, paid upgrade, new service or platform migration was performed.

The current Free service is **not adequate for a public demo expected to accept an arbitrary visitor at any time**. The supported next options are:

1. **Keep Free and ask Render to investigate this service** using the [prepared English report](RENDER-SUPPORT-REPORT.md). Cost: no new paid resource. Until resolved, accept that backend login/business APIs may be unavailable after sleep; no availability claim or periodic redeploy workaround is justified.
2. **Owner-approved paid compute on the existing service**: Render's published entry plan is **USD 7/month base compute**, `0.5c-512mb` (legacy Starter), 0.5 CPU / 512 MB. Benefits: no ordinary Free idle spin-down, five times the nominal CPU allocation, same public domain and no database/storage migration. Risks/limits: the memory allowance remains 512 MB, steady-state Tier 1 throughput has not been load-tested, bandwidth/build/other usage may incur separate costs, and upgrading cannot be promised to repair an unconfirmed platform-internal fault. Confirm the actual dashboard quote before purchase. Sources checked 2026-11-08: [pricing](https://render.com/pricing), [compute plan names](https://render.com/docs/compute-plans), [Free limitations](https://render.com/docs/free). **No purchase is authorized or executed by this report.**

No migration to another provider is proposed without an owner decision. Supabase database and private Storage can remain as they are for either option. Periodic ping is deliberately not enabled: it may reduce ordinary idle sleep, cannot recreate a missing instance reliably in these observations, consumes free hours, and would invalidate cold-start testing. The next acceptance after any platform remedy must repeat at least two genuine idle cycles (or validate paid always-on behavior), then have the owner perform real GitHub authorization. Mock/isolated regression and an enabled production button are not substituted for that real login.

## Verification follow-up publication

The follow-up changes documentation/evidence and credential-free diagnostic tooling only; application source, Docker settings and V1–V7 remain identical to deployed **585a941**. It uses `[skip render]` on the final merge to avoid redeploying solely to publish a report. GitHub CI remains enabled. Thus the final repository SHA and Render application SHA can legitimately differ; production version is checked against the last real deploy, not guessed from main. Final PR/CI/skip-event confirmation is reported with the handoff.

## Recovery boundaries

If the instance list is empty, preserve management state, logs, events and bounded network measurements before taking action. Do not delete tables, repair Flyway or rotate unrelated credentials. Frontend retry improves the visitor experience but cannot repair an unscheduled backend. A platform support report is maintained in [RENDER-SUPPORT-REPORT.md](RENDER-SUPPORT-REPORT.md); it is prepared for the owner and is not automatically sent.
