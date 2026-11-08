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

## Request

Please investigate service-specific HTTP spin-up/routing/instance scheduling during the incident, why restart did not create a new observable instance while same-version redeployment did, and whether a platform limitation or known incident affected this service. Please check internal scheduling and routing events unavailable through the public management API. We are not claiming a particular internal root cause.

The owner has not authorized a paid upgrade or data migration. No credentials, user information, database URI, OAuth state or access tokens are included. Application startup optimization and genuine idle-spin-up retests are documented in [PRODUCTION-STABILITY.md](PRODUCTION-STABILITY.md); subsequent results will be added without replacing this original evidence.
