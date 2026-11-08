# Render support report (prepared, not sent)

**Subject:** Free Docker service does not always spin up on HTTP traffic; restart accepted without new instance

Service: `caizhitong-api` / `srv-dbl9s43m8hqs738l94ig`, Singapore, Free Docker, Java 17, 512 MiB. Public health URL: <https://caizhitong-api.onrender.com/actuator/health>. All times below are UTC.

## Confirmed incident

- The account owner checked the dashboard: quota was available and there was no suspension/capacity message. Management API returned `not_suspended`, `suspenders=[]`, desired instances 1; the running instance list was empty.
- The last old application logs ended with a normal Hikari shutdown at **2026-11-07T07:43:44Z**.
- At **2026-11-08T09:13:57Z**, an independent GitHub-hosted Ubuntu runner requested health and OAuth configuration sequentially. Each request established TLS (approximately 70 ms / 29 ms), then timed out after 180 seconds with zero response bytes. Local network/direct/browser requests reproduced the failure. Evidence: [Actions run 42466298276](https://github.com/JouCristian/CaiZhiTong/actions/runs/42466298276). The diagnostic job eventually reached its overall timeout; it was not a successful probe.
- One official restart request was accepted at **2026-11-08T09:21:47Z**, with a `server_restarted` event. No new instance/startup logs appeared and HTTP requests still timed out.
- A single redeploy of the **identical frozen commit**, without changing configuration, was created at **2026-11-08T10:23:53Z**, deployment `dep-dbo4ree417fc73f98ju0`. A new instance started and the deployment became live at **10:26:37Z**. Health and OAuth then returned 200. This is recovery by redeployment, not proof that automatic spin-up is fixed.
- Historical successful starts took 114–144 seconds; the recovery start took 118.595 seconds. Flyway V7 and Hikari initialization succeeded. No actual OOM or database failure was established. We cannot infer a terminated instance's precise exit code from the available API.

## Additional reproductions

### Reproduced after the frontend recovery patch

The exact-validated frontend recovery PR was merged and deployed as `48fefa24a330a7d274a48b5d7411c5d5ad3363a6` at 2026-11-08T10:59:30Z. A warm external health/config check was successful at 11:20:00Z. The application then shut down normally at **11:46:48Z**; the instance list was empty before new traffic at approximately **13:26:44Z**.

No restart, redeploy, environment/plan change or business-data mutation occurred during the following experiment:

| UTC start | Network / endpoint | TLS time | Outcome |
| --- | --- | --- | --- |
| 13:28:04.850 | Local / health | 713 ms | 240.057 s timeout, no first byte |
| 13:28:13.770 | GitHub Ubuntu / health | 81 ms | 240.014 s timeout, no first byte |
| 13:32:04.919 | Local / OAuth config | 624 ms | 240.008 s timeout, no first byte |
| 13:32:13.786 | GitHub Ubuntu / OAuth config | 30 ms | 240.002 s timeout, no first byte |

The service was still `not_suspended`, desired instances 1, actual list empty at 13:32:53Z and 13:43:42Z. A log query at 13:41Z showed no new startup after the 11:46:48Z shutdown. Independent probe evidence: [Actions 42485090406](https://github.com/JouCristian/CaiZhiTong/actions/runs/42485090406), which correctly failed and retained JSON timing artifacts. The configured health path is `/actuator/health`, not the special `/robots.txt` path. A separate isolated CPU-quota experiment reduced Java startup costs but cannot account for the absence of any instance/startup logs in this failed wake cycle.

### Second genuine idle reproduction after measured startup optimization

We reduced JVM compilation overhead without disabling migrations, database/security checks or private storage verification. Exact validated PR #5 was deployed as **585a941b90972d6f3af637e2a922cfb6b198f1df**, deployment **dep-dbo7sk0ae00c739d8aeg**, live at **13:53:15Z**. Spring context started in **59.794 seconds**, compared with 114–119 seconds previously; post-runner readiness was 63.918 seconds. Warm health/config and desktop/mobile login-entry checks passed. No user/password/permission/schema/plan changes were made.

The final warm browser check ended by **13:55:46Z**, then all application probes stopped. At **14:10:44Z** the application shut down gracefully. Empty instances were confirmed at **14:11:36Z**, and both this new shutdown and the empty list were rechecked at **14:15:08Z** before traffic resumed. There was no intervening restart or redeploy.

| UTC start | Network / endpoint | TLS time | Outcome |
| --- | --- | --- | --- |
| 14:15:16.341 | Local / health | 862 ms | 240.091 s timeout, no first byte |
| 14:15:25.760 | GitHub Ubuntu / health | 42 ms | 240.015 s timeout, no first byte |
| 14:19:16.441 | Local / OAuth config | 991 ms | 240.011 s timeout, no first byte |
| 14:19:25.776 | GitHub Ubuntu / OAuth config | 41 ms | 240.002 s timeout, no first byte |

The service still reported not_suspended, desired=1 and zero instances at **14:19:58Z**; the instance list was still empty at **14:23:22Z**. A log read at 14:23:20Z showed no new startup since the 14:10:44Z shutdown. Independent evidence: [Actions 42488891370](https://github.com/JouCristian/CaiZhiTong/actions/runs/42488891370). It failed on the real timeout. Permanent sanitized evidence: [JSON](evidence/production-stability-2026-11-08.json). Please correlate internal spin-up/scheduling/routing records for **14:15–14:24Z**, as well as the earlier **13:28–13:44Z** window. Faster application startup did not resolve the absence of a newly running instance.

After both measured cycles completed, a fresh unauthenticated headless Chrome navigated to the backend root `/` at **14:24:45Z**. It ended in a navigation error after 102.948 seconds with no HTTP status; detailed browser timings were unavailable, so we do not claim a measured TLS success for this extra observation. At **14:28:00Z**, management state still showed zero instances, no new deploy/restart event and no startup logs after the 14:10:44Z shutdown. This was an additional read-only check, not a third idle cycle or an automatic recovery action.

## Requested investigation

Please investigate service-specific HTTP spin-up/routing/instance scheduling during the incident, why restart did not create a new observable instance while same-version redeployment did, and whether a platform limitation or known incident affected this service. Please check internal scheduling and routing events unavailable through the public management API. We are not claiming a particular internal root cause.

The owner has not authorized a paid upgrade or data migration. No credentials, user information, database URI, OAuth state or access tokens are included. Application startup optimization and genuine idle-spin-up retests are documented in [PRODUCTION-STABILITY.md](PRODUCTION-STABILITY.md); subsequent results will be added without replacing this original evidence.
