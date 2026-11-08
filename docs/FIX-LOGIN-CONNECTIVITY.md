# 登录连接故障专项 · 2026-11-08

分支：`fix/render-login-connectivity`。冻结标签 v0.7.0 保持原指向，本修复通过 PR 等待人工验收，不合并 main、不上线补丁、不改变生产数据库、权限、密钥或套餐。

## 结论分层

1. **前端缺陷已定位并修复**：OAuth 配置检测继承生产 240 秒业务超时，完成前 GitHub 按钮持续 loading / disabled；失败没有重连入口，也不自动重新检测。配置可用性变化还会卸载密码表单，可能丢失用户正在输入的内容。
2. **故障期间后端不是单纯的“多等一会儿”**：Render API 为 not_suspended，Free / Singapore / Docker、期望实例数 1，但运行实例列表持续为空。来自本机和 GitHub 托管运行器的 HTTPS 请求均在 TLS 成功后零字节超时，未出现相应新启动日志。受控重启已被接受，仍未观察到新运行实例。
3. **平台内部根因未被证实**：现象指向 Render 的唤醒 / 实例调度 / 路由未推进；不能凭外部证据确定具体内部子系统，也不能把数据库、OOM 或地域网络猜测写成事实。维护者查看控制台确认“无提示，额度未耗尽”。

4. **运行恢复已实测**：对同一冻结提交 ef5c178 重新部署后，18:26:37 Render live，实例列表恢复为 1，health 200 / UP、OAuth config 200 / true、CORS 正确，正式桌面 / 手机 GitHub 按钮恢复可用。没有部署本 PR，没有改环境或执行新迁移。

前端补丁能消除无限加载、恢复重试和密码入口体验，**不能替代失联后端**。本次运行恢复来自同版本重新部署，不代表 Free 自动唤醒路径已永久修好，也不代表前端补丁已上线。

## 初始故障的实际生产证据

以下时间为 2026-11-08，Asia/Shanghai；历史日志日期单独注明。原始日志及私密配置仅保存在忽略目录，未公开数据库连接或 Token。

| 检查 | 实际结果 |
| --- | --- |
| 服务状态 | caizhitong-api，Free，Singapore，not_suspended，suspenders=[] |
| 部署与配置 | 最后成功部署仍为 ef5c178，health path=/actuator/health，期望 numInstances=1 |
| 运行实例 | 多轮 GET /services/{id}/instances 返回空列表 |
| 配额 | API 未公开精确账单余额；账户持有人在控制台确认无暂停提示、额度未耗尽 |
| 本机代理路径 | health / oauth config 均约 25 秒、HTTP=000、TTFB=0；TLS 约 0.7 秒 |
| 本机直连 | health 约 25 秒、HTTP=000、TTFB=0；TLS 约 0.6 秒 |
| 独立网络 | GitHub Ubuntu 运行器两个接口各等待 180 秒、0 字节；TLS 分别约 0.070 / 0.029 秒 |
| 正式浏览器 | Vercel 登录页可打开，33 秒时 GitHub 按钮仍 loading / disabled；后端顶层 health 导航也在 30 秒超时 |
| CORS | 当前 HTTP 请求无响应头，不能实测成功 CORS；独立非浏览器 health 同样失败，因此 CORS 不是此次失联的充分解释 |
| 全局平台状态 | 检查时 Render status 为 All Systems Operational；这不排除单服务异常 |
| Supabase 本次只读探测 | 使用要求 SSL 的独立 JDBC 连接成功（两个样本约 1.9–3.2 秒），服务器 17.11、最新成功 Flyway V7；事务只读并回滚，无迁移或业务写入 |

独立网络一次性诊断记录：[Actions 42466298276](https://github.com/JouCristian/CaiZhiTong/actions/runs/42466298276)。17:13:57 开始，health 在 17:16:57、config 在 17:19:57 分别输出 180 秒超时；任务达到总超时而被取消，**不是成功测试**。临时诊断工作流随后移除，最终 PR 不保留生产轮询或定时保活任务。

### 历史启动与数据库

恢复前最近可见日志止于 **2026-11-07 15:43:44** 的正常 Hikari shutdown，之后的本次请求未产生新启动记录。历史多次 Spring Boot 启动约 114–144 秒，最长样本进程运行约 155 秒才准备完成。

2026-11-07 的样本：15:28:31 完成 Spring Boot 启动，15:28:33 Hikari 启动完成，15:43:43 开始 graceful shutdown，符合 Free 闲置约 15 分钟的行为。Flyway 成功校验并显示业务 Schema 当前版本 7，无新增迁移；验证数量包含 Schema 标记，不能误读为 V8。数据库日志显示 Supabase PostgreSQL 17.11，项目本地 / CI 兼容目标仍为 PostgreSQL 15。

这些成功启动中 Flyway 校验与迁移检查约数秒、Hikari 约 1–3 秒，没有观察到数据库超时或实际 OutOfMemoryError。JAVA_TOOL_OPTIONS 中的 ExitOnOutOfMemoryError 只是 JVM 配置，不是 OOM 发生证据。API 未返回实例信息，因此无法读取当前容器启动时间、已结束实例的精确退出码和 OOM 标记，不能伪造为 exit=0 / OOM=false。本次额外独立只读探测证明数据库本身可以连接，但不等于验证了当前 Render → Supabase 路径；Session pooler 后端 pg_stat_ssl 也不能当作客户端 TLS 的判断依据。

### 一次受控重启

核对套餐仍 Free、生产 Commit 仍为冻结版本后，通过官方 restart API 请求一次重启，不修改环境、代码、数据库或套餐。17:21:47 出现 server_restarted 事件；后续多轮仍为 0 实例、没有新日志，health 再次 20 秒零字节超时。事件被平台接受不等于应用已经启动成功，没有反复重启或清库。

### 同冻结版本重新部署与实际恢复

重启未推进后，18:23:53 通过 Render API 指定**原冻结 Commit ef5c178**发起一次缓存保留的重新部署，ID 为 `dep-dbo4ree417fc73f98ju0`，不是本修复分支。没有更新环境变量、密钥、权限、套餐或业务数据；没有新建服务或付费资源。

- 18:24:37 出现新的 Java / Spring Boot 日志，cloud profile；运行实例 API 返回 1。
- 18:25:36 Flyway 校验成功，18:25:38 当前 V7 且 No migration necessary，没有执行新迁移。
- 18:26:26 Spring Boot 启动完成，**118.595 秒**（进程运行 127.687 秒）；18:26:29 Hikari 启动完成。
- 18:26:37 部署状态 **live**，Commit 仍为 ef5c178。
- 真实 health：HTTP 200、UP、约 **1.145 秒**；config：HTTP 200、githubEnabled=true、约 **2.066 秒**。响应允许来源准确为正式 Vercel 域名。
- 正式浏览器实测：1440px 桌面约 **1.86 秒**、390px 手机宽度约 **3.08 秒**后 GitHub 入口 enabled 且无 loading，无横向溢出。只检查入口，不自动执行真实 GitHub 授权或创建生产用户。

相同代码与配置经重新部署恢复，结合故障时无实例 / 无日志，支持平台侧唤醒或路由未推进的判断；内部具体原因仍需 Render 确认。没有改成假健康检查，也没有为掩盖故障放宽 CORS 或 OAuth 校验。没有主动等待再次休眠 15 分钟再复测，不能将此次部署启动成功当作空闲自动唤醒已修复。

## 前端最小修复

- 新增 useGithubAvailability：配置探测独立 **8 秒**超时，不继承业务 240 秒超时。
- 首次探测后区分可用、明确关闭、可能唤醒 / 超时、网络失败；只有成功布尔值 false 才显示“GitHub 登录未启用”。无法准确判断休眠时使用“可能”，不伪装平台诊断。
- GitHub 按钮只在真实发起授权时 loading，探测时展示短时状态与解释，不保持无限转圈。
- 提供“重新连接”，一次失败后最多自动重试 **5 次**；延迟 5 / 15 / 30 / 45 / 45 秒，总共至多 6 次 GET。耗尽后停止，用户可主动开启新一轮；预算内后端恢复即更新入口，预算外需手动重连。
- 当前请求进行中合并重复操作，手动重连取消旧重试计时器；组件卸载取消请求和计时器，旧响应不能改变新页面。
- 账号密码入口始终独立可操作；表单保留挂载，明确收起 / 展开，开始输入后服务恢复也不自动隐藏。密码提交及 OAuth 发起均有同步防重锁。
- 通用 API 客户端增加单次超时参数、调用方 AbortSignal 支持及 timeout / network / aborted / protocol 分类；HTTP 异常或无效配置显示响应异常，不冒充网络失败或明确关闭。保留其他业务请求的超时、401 / 403 处理、文件下载和返回契约，不自动重试写请求。
- 后端 OAuth state、PKCE、60 秒一次性交换、GUEST 默认权限和正式 / Demo 隔离不变。

没有加入额外框架或依赖，没有改后端、Flyway、权限、健康检查或生产密钥。现有 Hikari 获取连接超时 15 秒、云池上限 3，当前证据不支持无依据改数据库连接或改为假健康检查。

## 验证与修正

测试仅使用本机独立数据库，Mock Provider 只处理合成资料。新增 8 个场景 × 桌面 / 手机 = 16 项，覆盖正常授权、8 秒挂起、503 后恢复、网络失败与重试上限、手动双击 / 旧定时器、待连接时密码登录 / 旧响应、明确关闭、非法响应与永久 HTTP 错误。原 OAuth / 业务回归保留。

| 命令 / 套件 | 结果 |
| --- | --- |
| npm --prefix frontend run typecheck | 通过 |
| npm --prefix frontend run build | 通过，保留已有 Ant Design 大块提示 |
| Maven Wrapper -B verify | 新隔离库 **195/195**，0 失败 / 错误 / 跳过，BUILD SUCCESS |
| Playwright 普通业务套件 | **70/70**，6.3 分钟 |
| 五组真实 HTTP 冒烟 | materials / stock / issues / lifecycle / administration 全部通过 |
| Mock OAuth / Demo / 视觉 / 连接专项 | 最终代码 **34/34**，58.1 秒，原 18 + 新增 16 |
| 侧栏 / 抽屉专项 | **6/6**，22.8 秒；登录辅助等待真实配置状态，不再依赖已取消的配置 spinner |
| 生产原版本恢复复核 | health / config / CORS 与正式桌面 / 手机入口通过；未自动进行真实 GitHub 授权 |

首轮 Maven 默认 caizhitong_test 残留早期未发布 V7 checksum，验证正常拒绝。改用新建 caizhitong_login_connectivity_test 后完整通过；没有改 V7、repair 或清理业务库。早期浏览器断言要求收起表单完全不存在，改为检查不可见，以保留输入；测试依旧验证展开、真实密码登录和权限，没有删用例或放宽安全断言。

本机手动测试服务端口为 8081 / 5180，设置 E2E_ISOLATED=1；普通套件 E2E_OAUTH=0，Mock 套件为 1。浏览器为安装的 Chrome，使用 E2E_BROWSER_EXECUTABLE，未提交本机路径或凭据。冻结标签、后端 API 契约与生产数据库基线未被覆盖。

补丁截图：[桌面超时回退](screenshots/login-connectivity-waking-desktop.png)、[手机超时回退](screenshots/login-connectivity-waking-mobile.png)。来自本机真实浏览器与隔离后端，通过测试注入配置请求挂起验证错误反馈，不冒充已部署的生产界面。

## Free 方案评估与下一步

Render 官方说明 Free 闲置 15 分钟休眠、请求触发唤醒，并有配额和容量限制，见 [Free 文档](https://render.com/docs/free)。本系统实际冷启动达到约两分钟，加上本次持续不能自动拉起实例，**不适合承诺“陌生访客随时可以稳定登录”**。当前已恢复，可继续偶尔课程展示；需要接受冷启动和可能的手工恢复，不能把有限前端重试当作可用性保证。

若继续保持 Free，可向 Render 支持提交下面的证据，确认单服务的路由 / 唤醒 / 调度状态，并观察再次空闲后的行为。不添加保活绕过套餐限制，不未经确认升级或迁移。若要稳定公开展示，优先评估不会休眠且内存 / CPU 足够的后端方案；可以是原平台常驻实例，也可以是其他托管平台，Supabase 数据与 Storage 无需因此立即迁移。付费可能解决休眠限制，但**不能保证防止尚未定位的内部平台故障复发**，应先确认故障或实际验证候选方案。

可供账户持有人提交的支持摘要（未自动发送）：

> Service srv-dbl9s43m8hqs738l94ig, Free Docker, Singapore. During the incident it reported not_suspended and desired numInstances=1, but GET instances returned an empty list. Local and GitHub-hosted runner requests established TLS and then received zero bytes for 180 seconds. Logs stopped at 2026-11-07T07:43:44Z. Restart accepted at 2026-11-08T09:21:47Z did not create new observable startup logs. Account holder confirms quota was available. Redeploying the identical frozen commit at 2026-11-08T10:23:53Z finally created a new instance and recovered health/config at 10:26:37Z. Please investigate why HTTP spin-up/restart did not progress while same-version redeploy did. No configuration change or paid upgrade is authorized.

## 人工验收

1. 本地隔离预览打开 /login，Mock OAuth 正常时 GitHub 入口可用；使用该入口只连接本机合成 Provider。
2. 模拟断网 / 挂起配置请求，8 秒后看到明确原因、“重新连接”和有限自动重试；密码入口仍能展开并填写。
3. 网络恢复后在预算内自动启用，手动重连不重复发请求；已经填写的表单不丢失。
4. Mock GitHub 登录后仅进入 Demo，正式业务 API 403；管理员密码与原业务正常。
5. PR 尚未合并上线；正式后端已由同版本重新部署恢复。请在日常浏览器自行刷新正式登录页验收，不导出个人令牌，不把本地补丁 UI 当成正式已部署。
