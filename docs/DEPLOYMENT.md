# 发布准备与部署说明

本文记录 v0.6.0 实际具备的配置入口及后续部署要求。尚未执行 Render、Neon、R2 或 Vercel 部署，不将配置准备写成上线成功。源码发布与 CI 状态见 [发布记录](RELEASE-v0.6.0.md)。

## GitHub 开源准备

1. Sprint 01–06 已由用户确认本地人工验收通过；源码发布前再次核对 README、实际截图、构建和测试。
2. 代码使用根目录 MIT 许可证；内嵌中文字体使用独立 OFL，保留 `backend/src/main/resources/fonts/` 的许可与派生字体说明。
3. `.env`、私有配置、上传附件、数据库数据、构建产物和测试日志均应保持忽略；`.env.example` 只含占位配置。提交前检查 `git status --short --untracked-files=all` 和暂存区 diff，不能因配置了 ignore 就省略审核。
4. `docs/screenshots` 与 `docs/examples` 有意保留真实测试截图及非敏感报表示例。它们来自隔离数据库，不能替换为实际学生名单或真实财务记录。
5. `.github/workflows/ci.yml` 已准备 Java / PostgreSQL 测试和前端构建；其中数据库密码为临时 CI 测试值，不是生产凭据。浏览器完整验收目前由 Windows 本地脚本执行。
6. 本轮仅发布源码并验证真实 CI；正式 GitHub Release、版本 tag 和公开体验地址在云端验收后另行发布。首次提交不回填六轮虚构 Git 历史。

## 前端：Vercel

- Root Directory：`frontend`
- Framework：Vite
- Install Command：`npm ci`
- Build Command：`npm run build`
- Output Directory：`dist`
- 配置 `VITE_API_BASE_URL=https://你的后端域名`，不要附加 `/api`，也不要存入任何服务端密钥。
- 已提供 `frontend/vercel.json` 的 SPA 重写，支持 `/reports`、`/settings` 和批次深层路由刷新。
- Vite 开发代理仅供本地；云端 API 必须是浏览器可访问的 HTTPS 地址。

## 后端：Render Docker

已提供 [backend/Dockerfile](../backend/Dockerfile) 多阶段构建与 [.dockerignore](../backend/.dockerignore)：Java 17、Maven Wrapper、非 root 运行、0.6.0 JAR，不复制本地 `.env` / target / 上传目录。当前没有本机 Docker 可执行环境，镜像构建及 Render 运行尚未验证。

构建阶段显式安装 curl / unzip，使用固定 SHA256 的 Maven ZIP 分发包；在其他 Unix 环境直接运行 Wrapper 也需安装 unzip。数据库密码和 JWT 密钥只在运行时注入，不使用 Docker ARG / ENV 写进镜像构建层。

后续在 Render 创建 Docker Web Service 时：

- Docker Build Context Directory：`backend`；Dockerfile 路径：`backend/Dockerfile`（按控制台字段的仓库相对路径填写）。本地等价构建命令为 `docker build -t caizhitong-backend:0.6.0 ./backend`。
- Dockerfile 自带 Java 启动命令，不填写 Vite / npm 启动命令。
- 设置 `SERVER_PORT=10000` 并与 Render 分配端口一致，健康检查路径 `/actuator/health`。Spring 默认监听所有网络接口；当前应用只读取 SERVER_PORT，不自动读取平台 PORT。
- `STORAGE_ROOT=/app/uploads`，必须挂载持久磁盘并验证 UID 10001 可写、重启后照片仍存在。免费临时磁盘不能作为可靠附件存储；需要持久磁盘时确认对应套餐能力。
- 不设置 dev profile，`DEMO_ENABLED=false`。Dockerfile 跳过构建期测试，数据库测试由发布前检查和 CI 执行。

服务端配置：

1. Java 17 / Maven 构建 `backend-0.6.0.jar`，将 PostgreSQL 数据库 URL、用户名、密码通过部署平台环境配置提供。
2. 不启用 dev profile，设置 `DEMO_ENABLED=false`；不公开开发演示账户。首次生产管理员应通过受控的一次性数据库初始化导入 BCrypt 哈希与 ADMIN 角色，初始化后由用户管理页维护；当前没有互联网公开注册或生产自助初始化接口。
3. `JWT_SECRET` 使用独立随机 Base64 密钥（解码至少 32 字节）；生产不能复用本地或测试密钥。
4. `CORS_ALLOWED_ORIGINS` 配置精确的前端 HTTPS 来源；`PUBLIC_WEB_URL` 使用该前端来源，供二维码与通知链接使用。
5. 新空库首次启动自动执行 V1–V6；已有库按版本追加迁移，禁止手改已执行脚本或关闭校验。部署前先备份数据库。
6. 初始业务时区取 `BUSINESS_TIME_ZONE`，未设置时 Asia/Shanghai。首次库存业务前可在系统设置调整；产生库存业务后锁定，不能通过普通设置接口追溯改变历史日期与额度月份。
7. 将 `STORAGE_ROOT` 指向持久卷，独立备份照片；不能依赖临时容器磁盘。当前 `AttachmentStorage` 是文件系统实现，对象存储需要后续适配，不能仅填环境变量切换。
8. 邮件凭据只配置在服务端：MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM 等；系统设置只展示准备状态并维护业务收件人。无配置时站内预警可用，但不会伪造邮件成功。
9. 验证 `/actuator/health`、真实登录、CORS、六种下载、照片鉴权和深层路由；反向代理允许必要的上传大小（单张 5 MB，multipart 请求最多 6 MB）。

## 数据库：Neon PostgreSQL

后续创建独立项目与数据库后，将控制台的连接信息拆成服务端环境变量：

```text
DB_URL=jdbc:postgresql://<Neon直接连接主机>/<数据库名>?sslmode=require
DB_USERNAME=<数据库角色>
DB_PASSWORD=<由平台安全配置的密码>
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
```

尖括号为占位值，不包含实际凭据。Neon 的 `postgresql://` 地址需转换成 JDBC 格式，不把密码拼进可公开日志或 README。优先使用直接 / 非池化主机完成 Flyway，当前迁移与运行共享同一数据源；本轮不承诺与所有事务池模式兼容。

启动时自动校验并执行 V1–V6；迁移角色需有建表、索引、序列与触发器权限。新库为空时不要上传日常数据库 dump；示例材料也应另行明确选择导入。已有生产库先备份，禁止修改旧迁移、强制 repair 或关闭校验来掩盖错误。连接池数量按 Neon 套餐限额调整。尚未验证 Neon 冷启动、连接额度或跨区延迟。

## 对象存储：当前边界与 Cloudflare R2 准备

**v0.6.0 未实现 S3 / R2 适配器。** 不能把 R2 Endpoint 填入 STORAGE_ROOT，也不能仅增加 Access Key 就宣称附件可用。当前可用方式是后端持久文件系统及既有鉴权下载接口。

若后续选用 R2，需要另轮开发并验证服务端存储适配：准备私有 Bucket、S3 兼容 Endpoint、限定 Bucket 权限的访问凭据，并通过后端安全环境配置管理；继续保留已有文件类型 / 大小 / 哈希校验和逐请求访问控制，不能直接公开学生附件。还需验证上传、认证读取、跨重启读取及历史本地附件迁移。此处为待办要求，本轮未创建 Bucket、未配置云凭据、未接入 SDK。

## 域名、跨域与演示账户

- 前端 `VITE_API_BASE_URL` 指向 HTTPS 后端来源；后端 `CORS_ALLOWED_ORIGINS` 精确列出 HTTPS 前端来源，不使用带凭据的任意来源。
- `PUBLIC_WEB_URL` 指向稳定前端公开域名，服务端用它生成二维码与邮件链接。上线后再生成 / 验证二维码；本地旧二维码不会因改配置自动重印。
- 后端不能把 SMTP、数据库、JWT 或对象存储凭据返回给前端；这些值均不得使用 `VITE_` 前缀。
- 本地三账户继续仅用于 dev。生产不沿用本地密码。若后续需要公开演示，使用独立演示库与独立受限账户，不放入真实学生、财务或附件数据；公开管理员账户可修改用户与系统，不能直接复用。
- 首个生产管理员仍需受控一次性导入 BCrypt 哈希与 ADMIN 关联，当前没有生产自助初始化入口。生产初始化及公开演示的重置策略须上线前单独验收。

当前是单实例课程项目方案，尚未执行 Docker / 云服务运行、生产压测、多实例附件共享、外部邮箱投递和实际财务系统验收。公网发布前需确认数据库与附件备份、HTTPS、账户初始化、磁盘可写性和隐私要求。
