# 云端部署说明

当前架构：GitHub main → Vercel React 前端 → Render Java 17 / Spring Boot → Supabase PostgreSQL + 私有 Storage。历史发布记录中的其他供应商方案已被本方案替代。

当前版本已按 [v0.7.0 冻结说明](RELEASE-v0.7.0.md) 固定应用范围。main 仍连接平台自动部署，文档合并也可能重建服务；冻结不代表关闭部署或自动回滚数据库。当前 UI 及最近发布证据见 [UI 精修](UI-POLISH-01.md)，维护 PR 流程见 [贡献指南](../CONTRIBUTING.md)。

**真实云部署已完成初始业务验收。** [前端](https://caizhitong-eight.vercel.app) · [后端健康](https://caizhitong-api.onrender.com/actuator/health)。历史本地验证见 [Cloud Deployment 01](CLOUD-DEPLOYMENT-01.md)，初始业务配置见 [Cloud Deployment 02](CLOUD-DEPLOYMENT-02.md)，当前 v0.7.0 生产 OAuth 与增量迁移验收见 [Cloud Deployment 03](CLOUD-DEPLOYMENT-03.md)。以下步骤供复现和维护；已有项目不要重复创建。

## 配置入口

v0.7.0 增加可选 GitHub OAuth，默认关闭；准确的 GitHub OAuth App 首页 / 回调及仅后端变量见 [OAuth 部署与验收](V0.7.0-GITHUB-OAUTH.md)。当前生产已经按用户授权合并 PR #1，并将私密 OAuth 凭据安全配置到 Render，GITHUB_OAUTH_ENABLED=true；账户持有人已在日常浏览器真实授权并复登。V7 已在私有业务 Schema 执行，包含 OAuth 身份与独立 Demo 表，V1–V6 保持不变。下面的 v0.6.0 初始云验收记录保留历史口径，最新验证以 Cloud Deployment 03 为准。

- 本地继续使用根 [.env.example](../.env.example)，默认 `local` 存储、`public` Schema，原三个开发演示账户逻辑不变。
- 云配置模板为 [.env.cloud.example](../.env.cloud.example)。真实值应由用户在平台安全环境变量中设置，或保存在忽略的 `.env.cloud`，不要粘贴到聊天、提交 Git 或写进 Docker 构建参数。
- `SPRING_PROFILES_ACTIVE=cloud` 启用云保护；仅复制模板不会自动部署。现有 `load-env.ps1` 只加载本地 `.env`，不会暗中切换至云库。

## Supabase PostgreSQL

沿用用户已经创建的 CaiZhiTong Free 项目（Mumbai），不要另建项目，不导入本地学生或财务数据。

在项目 Connect 页面读取准确主机、用户名和数据库名。当前已验证 Render 使用 **Session pooler 5432** 连接成功，不依赖 IPv6 Direct；长期运行的 JDBC 应用不使用 Transaction pooler 6543，不按区域猜测主机。实际云库 PostgreSQL 为 17.11，本地 / CI 继续兼容 PostgreSQL 15。

连接形式：

    DB_URL=jdbc:postgresql://<Connect 页面主机>:5432/<数据库>?sslmode=require
    DB_USERNAME=<Connect 页面用户名>
    DB_PASSWORD=<平台安全环境变量>
    DB_SCHEMA=caizhitong
    DB_POOL_MAX=3
    DB_POOL_MIN=0

JDBC URL 不嵌入用户名或密码。`cloud` profile 在迁移前拒绝未启用 SSL、URL 内凭据、6543 端口及不安全 Schema。证书校验选项不得通过关闭 TLS 来绕过；如使用 verify-full，需按 Supabase 提供的证书与主机配置验证。

### 独立 Flyway 连接与 Schema

`FLYWAY_DB_URL` / `FLYWAY_DB_USERNAME` / `FLYWAY_DB_PASSWORD` 为迁移配置；未单独设置时继承应用数据库配置，但 Spring Boot 仍创建 **独立 Flyway DataSource**，不从业务 Hikari 池借用迁移连接。需要时可为迁移和运行指定不同数据库角色。

云端默认仅在 `caizhitong` Schema 创建业务表与 Flyway 历史，业务连接也使用同一 Schema；允许以 `caizhitong_` 开头的受限自定义名称。运行 Schema 与迁移 Schema 必须相同，迁移 Schema 只能有一个。不会向 Supabase 的 auth、storage、extensions 或 public Schema 写入业务表。

v0.6.0 初始部署原样执行 V1–V6，v0.7.0 仅追加 V7，不改写旧脚本。始终启用 validate-on-migrate、禁用 clean；不使用 repair、关闭校验或删平台对象来掩盖问题。新建 Schema 时的非版本创建标记不计入迁移版本。

### Data API 防护

当前 Supabase Data API 已由用户关闭；后续如开启，必须确认 `caizhitong` **不在 Exposed schemas**。不要把业务表移到公开 API Schema。前端仍只访问 Spring Boot，不使用 Supabase anon key 直接查询业务表。

新私有 Schema 不授予 PUBLIC USAGE；已在真实 Supabase 检查 anon / authenticated 均无该 Schema USAGE，实际 Data API 请求不可用。不要在后续维护中向这些角色授予业务访问权限。

## Supabase 私有 Storage

通过 `STORAGE_MODE=local|supabase` 选择实现。默认本地模式保留原路径与文件持久化行为；Supabase 模式使用官方服务端 Storage REST API，不增加浏览器直传或公开图片 URL。

    STORAGE_MODE=supabase
    SUPABASE_URL=https://<项目引用>.supabase.co
    SUPABASE_STORAGE_BUCKET=caizhitong-attachments
    SUPABASE_STORAGE_KEY=<仅后端持有的服务端存储凭据>

沿用现有 **private** Bucket `caizhitong-attachments`，不要开启 Public；实际限制为 JPG / PNG / WebP 和 5 MiB。凭据只放后端环境变量，不使用 anon / publishable key，不放入任何 VITE_ 配置。后端启动会鉴权读取 Bucket 元数据，拒绝公开、缺失或无法验证的 Bucket。

应用保留原图片解码、5 MB / 2000 万像素限制和随机对象标识；上传成功后把对象标识、类型、大小、哈希和上传人保存到 PostgreSQL。所有读取继续经过 Spring Security、JWT、角色及附件归属检查，由后端代理返回，并设置 no-store / nosniff；不返回公开或长期签名 URL。上传不覆盖同名对象，数据库记录失败时尝试清理已上传对象。

已验证真实平台上传、服务端认证读取、匿名拒绝和 Render 重新部署后的相同照片哈希；原本机 loopback HTTP 夹具继续用于自动化回归。切换存储模式不会搬迁旧本地文件；已有附件迁移需要单独实施，不删除本地原件。

## 首个生产管理员

生产必须 `DEMO_ENABLED=false`，不启用 dev profile，不沿用本地演示密码。使用受控的一次性启动初始化：

    BOOTSTRAP_ADMIN_ENABLED=true
    BOOTSTRAP_ADMIN_USERNAME=<唯一用户名，3–64 位>
    BOOTSTRAP_ADMIN_PASSWORD=<新生成的 16–72 字节私人密码>

只有没有任何启用 ADMIN 时才创建账户；密码 BCrypt，使用与用户维护一致的数据库事务锁。并发启动只创建一个有效管理员。存在有效管理员时重复启动直接跳过，不重置密码；用户名已被其他账户占用则拒绝接管。没有公开注册或初始化 HTTP 接口。

成功验证首次登录后，删除一次性密码变量并设置 `BOOTSTRAP_ADMIN_ENABLED=false`。当前生产已完成这两个收尾步骤，重部署后登录正常且启用管理员为 1。保留开关和秘密会留下未来重新初始化的能力，不应长期保存；更换部署配置时不要重新加入密码。

## Render Docker 配置

现有服务名 caizhitong-api，Free，Singapore。不要重复创建；收费磁盘、套餐或资源升级需账户持有人确认。

- 仓库 `JouCristian/CaiZhiTong`，分支 main，Runtime Docker。
- Docker build context `backend`；Dockerfile 使用控制台要求的仓库相对路径 `backend/Dockerfile`。本地等价命令：`docker build -t caizhitong-backend:0.7.0 ./backend`。
- 健康检查 `/actuator/health`。cloud profile 优先 SERVER_PORT，其次平台 PORT，默认 10000；不要把后端作为 npm / Vite 服务启动。
- 环境设置为 cloud profile、Supabase 模式、独立 DB / Flyway 配置与新 JWT 密钥。JWT_SECRET 使用新生成、Base64 解码至少 32 字节的随机值，不复用本地密钥。
- 镜像以非 root 用户运行，JDK 17 容器感知堆上限为内存的 40%，Serial GC，限制 Metaspace / Code Cache / Direct Memory；cloud profile 限制 Tomcat 线程，数据库池默认最多 3 个连接。PR #5 增加并部署 `-XX:TieredStopAtLevel=1`，以降低小 CPU 实例的启动编译成本，可能牺牲峰值吞吐；若平台自定义了 JAVA_TOOL_OPTIONS，镜像默认值会被覆盖。正式启动约 59.8 秒，真实空闲唤醒须单独验收，见[稳定性记录](PRODUCTION-STABILITY.md)。
- GitHub CI 构建 Docker 镜像，并在 512 MiB 限制下连接临时 PostgreSQL，以 local 存储验证健康 / 401 / OOM 与重启次数。真实 Render 的独立部署结果见云部署记录。
- Free 实例本地磁盘不是附件的可靠持久层，实际部署必须使用 Supabase 模式。照片不会依赖 Render 本地目录跨部署保存。
- 启动失败需区分数据库连接 / SSL / 权限 / Schema、Bucket 鉴权和 OOM；不得把内存不足简单报告成数据库故障。真实平台还需观察冷启动、连续请求和重启指标。

## CORS、公开域名与通知

当前 `CORS_ALLOWED_ORIGINS` 和 `PUBLIC_WEB_URL` 均为 `https://caizhitong-eight.vercel.app`。更换域名时更新这两个变量并重新部署后端，再实际验证预检和二维码。禁止生产通配 CORS；预览域名不会自动获得生产 API 权限。

`PUBLIC_WEB_URL` 用于二维码和邮件链接，上线前需更新并重新生成相应标签；原二维码不会自动重印。SMTP 不是部署阻塞项：无配置时站内预警继续工作，邮件如实显示 NOT_CONFIGURED。

## Vercel 前端

现有 Hobby 项目 caizhitong 已关联 JouCristian/CaiZhiTong 的 main。Root Directory 为 frontend，Install `npm ci`，Build `npm run build`，Output dist，Node 24.x。生产域名由平台实际分配为 `https://caizhitong-eight.vercel.app`，不要猜测项目同名域名。

设置 `VITE_API_BASE_URL=https://caizhitong-api.onrender.com`（无 /api 后缀）、`VITE_API_TIMEOUT_MS=240000`。Vite 变量在构建时写入，修改后必须重新构建；禁止加入数据库、JWT 或 Storage 秘密。现有 vercel.json 支持 SPA 深层路由。生产域名公开访问，预览部署保留 Vercel 登录保护，且不在后端生产 CORS 中。

业务请求默认本地等待 12 秒，生产设为 240 秒。OAuth 配置探测独立为 8 秒，每轮最多 6 次 GET（首次 + 5 次有限自动重试），耗尽后停止，用户可手动重连；密码表单不被配置探测阻塞。接口不可达不能等同于 OAuth 未启用。该体验不能修复平台未调度实例的问题，也不保证平台可用性。

### 按需可用性与部署检查

- 本机：`node scripts/check-availability.mjs`。独立网络：GitHub Actions → **Production availability (on demand)** → Run workflow。仅请求公开 health/config，不需要业务账户或 JWT。
- 每个接口一个有总超时的 GET，保留状态、TLS / 首字节 / 总时长；失败退出非零，Actions 无论成功失败均保留 JSONL 30 天。配置格式、关闭状态和网络错误分开记录，不公开响应正文或密钥。
- 部署层面核对当前 main SHA、GitHub CI、Render 最新 deploy 的 commit/status/finishedAt 以及 Vercel **production** 的 commit/READY；不能用预览部署替代正式部署，不能把 HTTP 失败未经分析归因于部署失败。已有 Render Dashboard Events / Logs 提供部署失败证据，不新增带业务写权限的监控账户。
- 当前没有定时保活。低于 15 分钟的 Ping 可能减少普通空闲休眠，但会消耗免费实例时数，且不能保证修复未调度实例。真实空闲测试须关闭此类请求；完整操作边界与结果见[稳定性记录](PRODUCTION-STABILITY.md)。
- CPU / 内存启动对照是独立库实验：手动运行 **Build and test**（`gh workflow run ci.yml --ref main`）会追加 0.1 CPU / 512 MiB 配对测量；日常 Push / PR CI 保留完整原有回归与 512 MiB 冒烟，不必为文档提交重复约十分钟的性能实验。
- 仅文档维护可在最终合并提交中使用 Render 官方支持的 `[skip render]` 标记，保留 CI 并避免无意义重启；应用配置或源码修改不得借此跳过必要上线。实际部署 SHA 应与 main SHA 分开记录，见 [Render deploys](https://render.com/docs/deploys#skipping-an-auto-deploy)。

## 维护与后续验证

数据库连接与 TLS、私有 Schema、六版迁移、private Bucket、管理员初始化、HTTPS、业务闭环、六种导出、权限与跨部署照片均已独立验证，证据见 [实际记录](CLOUD-DEPLOYMENT-02.md)。每次修改基础配置后复查健康、登录、CORS、二维码和既有照片；不对日常数据运行重置 / 清理脚本。

继续关注免费资源额度、冷启动、内存、密钥轮换、数据库与附件配套备份。短期验收不替代长期压测或恢复演练。SMTP、自定义域名、正式 Release / tag 后续按需处理。
