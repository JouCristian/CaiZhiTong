# 云端部署说明

当前确定架构：GitHub 源码 → Render Java 17 / Spring Boot → Supabase PostgreSQL + 私有 Storage。Vercel 前端属于下一阶段。历史发布记录中的其他供应商方案已被本方案替代，不代表当前部署选择。

**当前只完成适配与本地隔离验证，尚未完成真实云端部署。** 本轮不控制浏览器、不创建 Render / Vercel 服务、不修改线上 Supabase 数据、不执行生产管理员初始化，也不开通付费资源。实际结果见 [Cloud Deployment 01](CLOUD-DEPLOYMENT-01.md)。

## 配置入口

- 本地继续使用根 [.env.example](../.env.example)，默认 `local` 存储、`public` Schema，原三个开发演示账户逻辑不变。
- 云配置模板为 [.env.cloud.example](../.env.cloud.example)。真实值应由用户在平台安全环境变量中设置，或保存在忽略的 `.env.cloud`，不要粘贴到聊天、提交 Git 或写进 Docker 构建参数。
- `SPRING_PROFILES_ACTIVE=cloud` 启用云保护；仅复制模板不会自动部署。现有 `load-env.ps1` 只加载本地 `.env`，不会暗中切换至云库。

## Supabase PostgreSQL

沿用用户已经创建的 CaiZhiTong Free 项目（Mumbai），不要另建项目，不导入本地学生或财务数据。

后续经用户授权部署时，在项目 Connect 页面读取准确主机、用户名和数据库名。先确认 Render 的 IP 出站能力：IPv6 可用时可选择 Direct；IPv4 环境选 **Session pooler 5432**。当前长期运行的 JDBC 应用不使用 Transaction pooler 6543，不按区域猜测主机。

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

V1–V6 不改写、不新增版本，仅在新私有 Schema 原样执行。始终启用 validate-on-migrate、禁用 clean；不使用 repair、关闭校验或删平台对象来掩盖问题。新建 Schema 时 Flyway 可能额外记录一条 Schema 创建标记，它不是 V7。

### Data API 防护

后续必须在 Supabase Data API 配置中确认 `caizhitong` **不在 Exposed schemas**，或在不影响 Storage 的前提下关闭不需要的 Data API。不要把业务表移到默认公开 API Schema。前端仍只访问 Spring Boot，不使用 Supabase anon key 直接查询业务表。

新私有 Schema 不授予 PUBLIC USAGE；真实 Supabase 上还需核对 anon / authenticated 等平台角色的 Schema / 表权限及 Data API 实测。当前本地测试不能替代此平台验收，也不会擅自更改线上权限。

## Supabase 私有 Storage

通过 `STORAGE_MODE=local|supabase` 选择实现。默认本地模式保留原路径与文件持久化行为；Supabase 模式使用官方服务端 Storage REST API，不增加浏览器直传或公开图片 URL。

    STORAGE_MODE=supabase
    SUPABASE_URL=https://<项目引用>.supabase.co
    SUPABASE_STORAGE_BUCKET=caizhitong-attachments
    SUPABASE_STORAGE_KEY=<仅后端持有的服务端存储凭据>

后续由用户在现有项目创建或选择 **private** Bucket `caizhitong-attachments`，不要开启 Public；建议限制为 JPG / PNG / WebP 和 5 MB。凭据只放后端环境变量，不使用 anon / publishable key，不放入任何 VITE_ 配置。后端启动会鉴权读取 Bucket 元数据，拒绝公开、缺失或无法验证的 Bucket。

应用保留原图片解码、5 MB / 2000 万像素限制和随机对象标识；上传成功后把对象标识、类型、大小、哈希和上传人保存到 PostgreSQL。所有读取继续经过 Spring Security、JWT、角色及附件归属检查，由后端代理返回，并设置 no-store / nosniff；不返回公开或长期签名 URL。上传不覆盖同名对象，数据库记录失败时尝试清理已上传对象。

已在本机 loopback HTTP 夹具验证协议、鉴权和重新创建后端存储实例后的读取；**真实服务端凭据、平台网关、Bucket 和重新部署持久性仍待云端验证**。切换存储模式不会搬迁旧本地文件；已有附件若要迁移，需要单独实施，不删除本地原件。

## 首个生产管理员

生产必须 `DEMO_ENABLED=false`，不启用 dev profile，不沿用本地演示密码。使用受控的一次性启动初始化：

    BOOTSTRAP_ADMIN_ENABLED=true
    BOOTSTRAP_ADMIN_USERNAME=<唯一用户名，3–64 位>
    BOOTSTRAP_ADMIN_PASSWORD=<新生成的 16–72 字节私人密码>

只有没有任何启用 ADMIN 时才创建账户；密码 BCrypt，使用与用户维护一致的数据库事务锁。并发启动只创建一个有效管理员。存在有效管理员时重复启动直接跳过，不重置密码；用户名已被其他账户占用则拒绝接管。没有公开注册或初始化 HTTP 接口。

成功验证首次登录后，删除一次性密码变量并设置 `BOOTSTRAP_ADMIN_ENABLED=false`。仅在没有有效管理员且明确启用该开关时才允许初始化；保留开关和秘密会留下未来重新初始化的能力，因此不应长期保存。初始化逻辑已在独立测试库验证，**本轮没有在生产执行**。

## Render Docker：后续执行步骤

本轮只准备并测试代码，不创建服务。后续选择 Free；遇到收费磁盘、套餐或网络能力确认时交给用户处理，不自动升级。

- 仓库 `JouCristian/CaiZhiTong`，分支 main，Runtime Docker。
- Docker build context `backend`；Dockerfile 使用控制台要求的仓库相对路径 `backend/Dockerfile`。本地等价命令：`docker build -t caizhitong-backend:0.6.0 ./backend`。
- 健康检查 `/actuator/health`。cloud profile 优先 SERVER_PORT，其次平台 PORT，默认 10000；不要把后端作为 npm / Vite 服务启动。
- 环境设置为 cloud profile、Supabase 模式、独立 DB / Flyway 配置与新 JWT 密钥。JWT_SECRET 使用新生成、Base64 解码至少 32 字节的随机值，不复用本地密钥。
- 镜像以非 root 用户运行，JDK 17 容器感知堆上限为内存的 40%，Serial GC，限制 Metaspace / Code Cache / Direct Memory；cloud profile 限制 Tomcat 线程，数据库池默认最多 3 个连接。
- GitHub CI 将实际构建 Docker 镜像，并在 512 MiB 限制下连接临时测试 PostgreSQL、以 local 存储启动验证健康 / 401 / OOM 与重启次数。这不代表 Render 或 Supabase 已部署。
- Free 实例本地磁盘不是附件的可靠持久层，实际部署必须使用 Supabase 模式。照片不会依赖 Render 本地目录跨部署保存。
- 启动失败需区分数据库连接 / SSL / 权限 / Schema、Bucket 鉴权和 OOM；不得把内存不足简单报告成数据库故障。真实平台还需观察冷启动、连续请求和重启指标。

## CORS、公开域名与通知

Vercel 尚未部署时，`CORS_ALLOWED_ORIGINS` 和 `PUBLIC_WEB_URL` 可以留空；这不开放任意生产跨域，受控 API 脚本仍可验证后端。下一阶段填入稳定 HTTPS 前端来源，不能使用通配 CORS。

`PUBLIC_WEB_URL` 用于二维码和邮件链接，上线前需更新并重新生成相应标签；原二维码不会自动重印。SMTP 不是部署阻塞项：无配置时站内预警继续工作，邮件如实显示 NOT_CONFIGURED。

## Vercel：下一阶段，不在本轮执行

前端 Root Directory 为 frontend，Build Command `npm run build`，Output Directory dist。设置 `VITE_API_BASE_URL=https://<已验收后端域名>`，不附加 /api，不存服务端秘密；现有 vercel.json 支持 SPA 深层路由。只有后端、数据库、附件与权限云端验收通过后才进入前端部署。

## 真实云端验收待办

Supabase Connect / SSL / Data API 权限、原始六版迁移实际执行、private Bucket 与服务端凭据、生产管理员初始化、Render 免费 Docker 构建与 HTTPS 启动、业务闭环与六种导出、照片跨重启 / 再部署保留、低内存稳定性均需单独实际验证。未部署的能力不标记成功。最终版本标签、GitHub Release 和在线体验链接继续等待云端验收。
