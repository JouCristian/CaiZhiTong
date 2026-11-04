# Cloud Deployment 03 · v0.7.0 GitHub OAuth 受控上线

日期：2026-11-04（Asia/Shanghai）。本记录区分生产实测与待完成项；没有创建 Release、付费资源或新 UI 功能。

## 合并与部署

用户明确批准后，核对 PR #1 Head 为 `08e0a0d50e26bf7c1ca57f8055fbf6369426c959`、全部检查成功且可合并，先安全准备 Render OAuth 凭据并保持开关 false，再正常 Merge，保留原历史。

- [PR #1](https://github.com/JouCristian/CaiZhiTong/pull/1)：MERGED。
- main 合并提交：`4cc92227556f3277f7b34c739dc9d70cc43c4b2a`。
- [main CI 41805598439](https://github.com/JouCristian/CaiZhiTong/actions/runs/41805598439)：success。
- Render 自动部署 `dep-dblfe4avcj2c738m2fog`：live，实际运行合并提交。启动约 137.7 秒。
- Vercel `dpl_G9bGNjFgp5VTebvVR5S2G6yJ46Ys`：READY、target=production，正式域名解析到合并提交。没有将预览部署当作正式上线。

| 服务 | 正式地址 |
| --- | --- |
| 前端 | https://caizhitong-eight.vercel.app |
| 后端 | https://caizhitong-api.onrender.com |
| 健康检查 | https://caizhitong-api.onrender.com/actuator/health |
| OAuth 回调 | https://caizhitong-api.onrender.com/login/oauth2/code/github |

## OAuth 关闭阶段的生产基础验收

- PostgreSQL SSL / 私有 caizhitong Schema，V1–V7 成功且逐文件 checksum 与源码一致；V1–V6 未修改，无 repair、清库或回退迁移。
- 合并前只读保存原记录指纹，迁移后逐条核对：原分类、材料、批次、入库、领用、归还、报废、流水、附件元数据和原账户状态 / 密码哈希均未改变。指纹文件只保留本机忽略目录，不公开用户数据。
- Demo 初始化为 5 分类 / 25 材料 / 50 批次 / 100 示例变化；OAuth 身份最初为 0。
- 健康 UP，OAuth config=false，原管理员密码登录成功；正式前端工作台与九个业务菜单打开正常。
- 原云验收材料、余额 82、私有照片 SHA-256 复验通过，共 6 个 HTTP 检查。
- 另外新建独立合成云验收数据，不导入本地学生资料。完整链路 35 个 HTTP 检查通过：100 入库 − 20 领用 + 5 归还 − 3 报废 = 82；净消耗 15，单价 0.1234，净金额 1.851；入库幂等、领用 PDF、三类报表的六种 Excel / PDF、私人照片上传 / 读取与 401 / 403 正常。临时合成学生最终停用，旧 JWT 401，不公开临时密码。
- 再次比较所有原有记录指纹，保持一致。

## 启用与真实身份验收

基础验收通过后才将后端 GITHUB_OAUTH_ENABLED 改为 true，并启动 Render 受控部署 `dep-dblfjcgae00c73c4cqpg`。准确 Client ID / Secret 由本机私密配置安全传递，未输出到聊天、Git 或日志。现有管理员回归密码未上传 Render；BOOTSTRAP_ADMIN_ENABLED=false，DEMO_ENABLED=false，无一次性管理员密码。

Vercel 环境仅 VITE_API_BASE_URL 与 VITE_API_TIMEOUT_MS，不配置 Client Secret。CORS / PUBLIC_WEB_URL 均为正式前端源，生产 Cookie 使用 Secure / HttpOnly / SameSite=Lax。

启用部署已 live，启动约 124.8 秒，配置接口返回 githubEnabled=true；健康 UP。再次启动后 Demo 数量保持 5 / 25 / 50 / 100，原业务记录指纹不变；新云验收批次余额 82 与新私有照片 SHA-256 仍一致，6 个持久化 HTTP 请求通过。

真实 GitHub 授权由账户持有人亲自在日常浏览器完成，并再次退出 / GitHub 登录确认复登成功。生产 oauth_identity 始终为 1，provider + 数字 ID → 本地 user_id 的哈希指纹一致；最新登录时间更新。初始角色仅 GUEST、password_login_enabled=false、账户启用。管理员通过既有用户 API 将该 GitHub 账户暂时分配 STUDENT，再恢复 GUEST，角色读回一致，未重置真实账户密码。

生产权限矩阵使用另一个独立合成 GUEST 验收账户的真实密码登录 JWT：27 个正式 GET（材料列表 / 详情 / 类目 / 库存 / summary / 批次 / 二维码 / 工作台 / 流水 / 入库 / 领用 PDF / 归还 / 报废 / 私人附件 / 用户 / 配置 / 报表导出）和 9 个写入请求均 403。Demo 查询仅 25 条，搜索正式云验收名称为空，详情两个批次与示例变化完整。对同一 JWT 授予 STUDENT 后正式材料请求成功、用户管理仍 403；撤回 GUEST 后下一正式材料请求立即 403。该合成权限账户最终停用，不删除记录。

真实账户的授权 / 复登及默认角色由本人和生产数据库核验；API 矩阵及同 JWT 权限变化用合成账户实测。没有从日常浏览器提取真实 OAuth JWT，没有把合成测试冒充真实账户的直接 API 测试。

正式站桌面原管理员工作台与九个业务菜单、手机密码次级入口 / 工作台 / 导航正常。桌面合成 GUEST 默认进入 Demo 工作台，可查询分页材料和批次详情。真实截图：[正式桌面登录](screenshots/cloud-v070-login-desktop.png)、[正式手机登录](screenshots/cloud-v070-login-mobile.png)、[正式演示工作台](screenshots/cloud-v070-demo-desktop.png)。截图不含密码、Token 或真实 GitHub 个人资料。

GitHub 曾拒绝自动化 Chrome 登录，已停止该路径，改为账户持有人日常浏览器亲自授权成功，未伪装浏览器或绕过安全检查。随后浏览器连接连续超时，不再控制持有人窗口；没有将未观察到的真实账户界面步骤记为自动化通过。生产伪造回调负向冒烟命令被自动审批拒绝（blocked by policy），未执行或绕过；对应 state / 重放等负向用例仍以已通过的本地 / CI 隔离测试为证据。一次 Demo 查询遇到网络超时，延长网络等待后只读重试通过；未发现实际 OOM 错误或反复重启。

## 真实 OAuth 创建后的重部署持久化

持有人完成真实授权与复登、角色恢复 GUEST 后，受控重新部署同一合并版本：Render `dep-dblko1cipgvc73ec1vh0` 已 live，启动约 125.8 秒。随后实际核验：

- V1–V7 checksum 全部匹配；合并前所有正式记录指纹仍保持，不删除或覆盖旧数据。
- Demo 仍为 5 分类 / 25 材料 / 50 批次 / 100 条变化，没有重复初始化；金额仍按币种独立。
- 真实 OAuth 身份仍为 1，稳定映射指纹一致，仅 GUEST、密码入口关闭，重部署未丢失用户。
- 管理员密码登录、原认证 401 正常；新云验收批次余额 82、材料和认证照片 SHA-256 保持一致，6 个 HTTP 请求通过。
- 发布候选 / Git 历史敏感信息扫描通过；真实密钥、管理员回归密码、数据库数据和测试日志均未进入 Git。

本次上线记录与截图通过正常 main Commit 发布，继续由相同 GitHub Actions、Render 和 Vercel 自动部署。后续文档 Commit 不改变已验收业务代码；最新部署 Commit / 状态在最终交付报告中读取实值，不将构建中或预览部署写成成功。

## 恢复与限制

OAuth 发生问题时优先将 Render 后端 GITHUB_OAUTH_ENABLED=false 并重新部署，保留用户名密码登录。禁止删除生产表、回退已执行的 Flyway 或用 repair 掩盖错误。

Free 休眠、冷启动和配额仍存在。握手 Session 在内存中，部署重启会使尚未完成的授权需要重新发起；已完成 OAuth 身份存于 PostgreSQL。没有新增刷新令牌，JWT 到期重新登录。SMTP 未配置时仍为 NOT_CONFIGURED，站内预警保留。本次不代表长期压力测试或灾难恢复演练。
