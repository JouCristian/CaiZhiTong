# Cloud Deployment 01 · 云适配与本地验证

日期：2026-11-04（Asia/Shanghai）。项目版本保持 0.6.0，不是新的业务 Sprint。

## 本轮边界

用户调整优先级为：完成已编写云适配、隔离测试、安全审计、提交 / Push 与远程 CI。明确暂停浏览器控制、Render / Vercel 创建、线上 Supabase 写入、生产初始化及付费资源操作。本轮仅执行本地和 CI 测试，不把云端步骤标记已完成。

上一轮只读确认用户已有的 Supabase CaiZhiTong Free 项目为 Healthy / Mumbai；本轮未重新读取或修改平台。当前架构确定为 Supabase PostgreSQL + private Storage、Render 后端，Vercel 留待下一阶段。历史发布文档保留当时事实，现行配置以 [部署说明](DEPLOYMENT.md) 为准。

## 实际新增能力

- `local` / `supabase` 按配置选择唯一附件实现。local 为原默认；Supabase 使用服务端 REST API，启动时检查 private Bucket，拒绝公开桶和不安全 URL，不向前端提供公开 / 长期签名 URL。
- 文件仍由原 AttachmentService 验证格式、5 MB / 2000 万像素、UUID 标识与权限。对象成功后提交原附件元数据；读写失败不输出凭据或供应商响应体，数据库失败尝试清理对象。
- Flyway 显式配置独立 DataSource；默认继承已解析的业务连接属性，保证测试动态 URL 不会误退回日常 DB_URL。业务 Hikari 默认最多 5 个连接，cloud 默认 3 个。
- cloud 使用独立 caizhitong Schema，迁移前验证业务 / Flyway Schema 一致且唯一，拒绝平台 / public Schema、非 SSL、URL 内凭据和 6543。保持 validate 开启、clean 关闭，V1–V6 不修改。
- 首生产管理员默认不初始化；明确开关启用且无有效管理员时，事务锁内校验唯一用户名和密码、BCrypt 保存、赋予现有 ADMIN。重复 / 并发启动不重复创建，不重置密码，不接管已存在用户名，无注册 HTTP 接口。
- Render 端口回退 PORT，低内存 JVM / Tomcat / 连接池配置。CI 新增实际 Docker 构建与 512 MiB 启动检查，但不是云部署。
- 云模板只含占位符，真实 .env.cloud 保持忽略；审计同时检查云私密文件值和 Supabase secret-key 特征。

## 隔离验证方式

1. JUnit 仅允许独立 *_test 数据库，云适配用其中的 `caizhitong_cloud_test` Schema；清理前同时检查数据库名和 Schema，绝不清理日常库。
2. 原 V1–V6 在该私有 Schema 原样执行；验证 Flyway 实际连接对象独立、六版校验、角色与业务表，并检查 PUBLIC 无 Schema USAGE。
3. Supabase 协议夹具仅监听 127.0.0.1。注入的 HTTP transport 将所有请求固定路由到本机，真实执行 POST / GET / HEAD / DELETE；不访问任何 Supabase 项目。
4. 通过完整 Spring Security / JWT / Controller / Service 与真实 PostgreSQL 测试初始化、登录、401 / 403、照片上传 / 读取、非法图像拒绝、材料 / 入库 / 带照片报废和重新查询。
5. 新建存储实例及数据库连接仍读到同一对象与元数据，只证明隔离环境持久化，不等于 Render 重启 / 重新部署已验证。
6. 原 147 项后端与 64 项浏览器继续全量回归，前端执行类型检查与生产构建。浏览器测试为既有 headless 测试脚本，不控制用户 Chrome。

## 实际修复

- 首轮编译发现 AssertJ 版本的 hasMessageNotContaining 仅接受单参数，改为链式断言。
- 新集成测试直接读取 @Repository 代理的 public 字段得到空值，改为注入 JdbcTemplate；不改既有生产 Mapper 架构。
- Flyway 自动创建私有 Schema 会多一条非版本 Schema 标记。测试改为明确核对版本 1–6，而非把所有历史行数误认为版本数；未创建 V7、未改历史迁移。

## 测试结果

| 执行项 | 实际结果 |
| --- | --- |
| 后端 `./mvnw.cmd -B verify` | 173 / 173，失败 0、错误 0、跳过 0；JAR 打包成功 |
| 新增自动化 | Storage 13 项、云连接 / Schema 5 项、真实测试库集成 8 项，共 26 项；原 147 项保留 |
| 前端 `npm.cmd --prefix frontend run typecheck` | 通过 |
| 前端 `npm.cmd --prefix frontend run build` | 通过；保留既有 vendor 大包提示 |
| `./scripts/test-browser.ps1` | 原有桌面 / 移动 64 / 64，0 失败、0 跳过，5.1 分钟；包含五组真实 API 冒烟 |
| 安全审计与迁移对比 | 两个发布审计脚本通过；V1–V6 与已发布 origin/main 无差异 |
| 真实 Supabase / Render / 生产初始化 | 未执行，按本轮要求暂停 |

真实 Supabase 连接 / 密钥 / Data API / Bucket、Render 构建与公网运行、生产初始化、云照片跨部署持久性均保留为待验证。CI Docker 构建 / 512 MiB 运行是隔离测试，不代表 Render 已创建。

远程 CI 在 Push 后执行，读取最新 main 的[真实工作流状态](https://github.com/JouCristian/CaiZhiTong/actions/workflows/ci.yml)。工作流构建镜像并在 512 MiB 容器连接临时 PostgreSQL、以 local 模式验证启动 / 健康 / 401 与 OOM / 重启计数；不能由此推断真实 Supabase Storage 已验收。最终交付应引用当次实际 Actions 运行，而非预写成功。

## 主要文件

| 文件 | 作用 |
| --- | --- |
| `backend/src/main/java/cn/caizhitong/service/LocalAttachmentStorage.java` | 保留本地行为，按模式启用 |
| `backend/src/main/java/cn/caizhitong/service/SupabaseAttachmentStorage.java` | 私有桶校验、服务端对象读写与清理 |
| `backend/src/main/java/cn/caizhitong/config/AttachmentStorageConfiguration.java` | 有时限、禁用重定向的存储 HTTP transport |
| `backend/src/main/java/cn/caizhitong/config/CloudDatabaseConfiguration.java` | 迁移前 SSL / Schema / 连接保护 |
| `backend/src/main/java/cn/caizhitong/config/ProductionAdminInitializer.java` | 默认关闭的首次管理员初始化 |
| `backend/src/main/resources/application.yml` / `application-cloud.yml` | 独立迁移连接、模式、连接池及端口配置 |
| `backend/src/test/java/cn/caizhitong/config/CloudDatabaseConfigurationTest.java` | 连接与 Schema 约束 |
| `backend/src/test/java/cn/caizhitong/service/` | Storage 单测、本机 HTTP 夹具、私有 Schema 集成 |
| `backend/Dockerfile` / `.github/workflows/ci.yml` | Java 17 内存配置及 Docker 构建 / 启动验证 |
| `.env.cloud.example` / `.gitignore` / `scripts/*release*` | 占位模板、私密配置忽略及审计 |
| `README.md` / `docs/DEPLOYMENT.md` / `database/README.md` / `docs/API.md` | 当前架构、兼容性及验证边界 |

## 后续平台操作

待用户验收本地适配后，再读取 Supabase Connect 的准确 Session / Direct 参数，检查 SSL / Data API，创建或选择私有 Bucket，用户在后端安全环境变量中提供凭据。随后选择 Render Free、读取真实部署日志并验证业务和内存；仅这一步通过后才准备 Vercel。不得要求用户向聊天粘贴密码或 Token，不得复用本地演示密钥，不开通付费资源。
