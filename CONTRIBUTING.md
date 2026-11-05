# 参与贡献

感谢你愿意改进材智通。本文面向第一次提交 PR 的开发者，也供维护者执行评审和发布。先阅读 [README](README.md)、[冻结说明](docs/RELEASE-v0.7.0.md)及与你改动相关的 API / Sprint 记录。

## 版本与贡献范围

v0.7.0 已冻结。冻结标签不可移动，V1–V7 迁移不可改写；冻结不等于仓库归档。文档、可复现缺陷、测试、无障碍和兼容性改进继续接收，修复在新提交中完成。新功能、权限变化、统计口径、依赖或数据模型调整请先开 Issue，评审后安排后续版本。

**main 关联正式部署。** 合并会触发 Vercel / Render，不要直接向上游 main Push。外部贡献不需要生产账户、数据库、GitHub OAuth Secret 或云平台权限；使用自己的本地环境、合成数据及 Mock Provider。

### 适合首次贡献的工作

- 根据实际启动过程修正文档、失效链接、说明不清的错误提示。
- 补充可复现问题的最小测试、键盘 / 小屏幕 / 减少动态效果检查。
- 在已有设计系统内修正具体布局问题，不另建一套模板。
- 提交有明确输入、实际结果和预期结果的缺陷报告。

不要把新框架引入、全仓格式化或数据库重构混入一个小修复。维护者可能要求拆分 PR；暂不接受某方向不代表否定你的投入。

## 第一步：反馈或讨论

先搜索 [现有 Issues](https://github.com/JouCristian/CaiZhiTong/issues) 和 [PR](https://github.com/JouCristian/CaiZhiTong/pulls)，避免重复。

- 缺陷：使用 Bug 模板，写复现步骤、版本、角色、浏览器 / 系统及预期结果。
- 新能力：说明谁遇到什么问题、当前替代方案、变更范围及权限 / 数据影响。
- 小型文档修正可直接提 PR；较大实现先讨论，不假设一定会合并。
- 漏洞、凭据和私人资料不要公开，按 [SECURITY](SECURITY.md) 处理。
- 普通使用问题见 [SUPPORT](SUPPORT.md)，不要将 Free 冷启动直接视为数据丢失。

## 第二步：Fork 与开发分支

在 GitHub 点击本仓库右上角 **Fork**，创建自己的副本。以下 PowerShell 中把用户名替换成你的账号：

```powershell
$githubUser = 'YOUR_GITHUB_USERNAME'
git clone "https://github.com/$githubUser/CaiZhiTong.git"
Set-Location ./CaiZhiTong
git remote add upstream https://github.com/JouCristian/CaiZhiTong.git
git remote -v
git fetch upstream
git switch -c fix/drawer-focus upstream/main
```

`origin` 是自己的 Fork，`upstream` 是 JouCristian/CaiZhiTong。分支名可用 `fix/...`、`feat/...`、`docs/...`、`test/...`，应能看出目的。不要在冻结标签上直接开发；从最新 upstream/main 建分支，方便评审与合并。

Git 提交身份使用自己的名字和邮箱；需要隐私时使用自己 GitHub Settings 中的 noreply 邮箱，不复制维护者身份。

## 第三步：建立本地环境

按 [README 本地快速开始](README.md#本地快速开始) 准备 JDK 17、Node 24.22.1、npm、PowerShell 与 PostgreSQL 15。

```powershell
./scripts/setup-local.ps1
Copy-Item ./frontend/.env.example ./frontend/.env
npm.cmd --prefix frontend ci
./scripts/portable-postgres.ps1 start
```

首次操作才复制前端配置，已有 `.env` 不覆盖。根 `.env` 由 PowerShell 脚本加载，Spring Boot 不自动读取；IDE 运行须自己配置环境变量。真实配置不跟踪、不发到 PR。

启动两个终端，根目录分别运行：

```powershell
./scripts/start-backend.ps1
```

```powershell
npm.cmd --prefix frontend run dev
```

本地 5173 / 8080 是日常环境，5180 / 8081 / 9098 是独立测试端口。测试会写合成记录，不能指向云库或真实业务库。

### Docker 与其他系统

Docker Compose 仅启动 PostgreSQL，默认只建日常 caizhitong 库。不要与便携实例争用同一端口。使用 Docker 测试后端时，可在根目录加载环境后手动建立一次测试库：

```powershell
. ./scripts/load-env.ps1
docker compose up -d
docker compose exec postgres createdb -U $env:DB_USERNAME caizhitong_test
docker compose exec postgres createdb -U $env:DB_USERNAME caizhitong_v070_demo_e2e_test
```

库已存在时无需重复 createdb，不删除旧库解决重复提示。根 `.env` 的 DB_URL 应指向本机 Docker 端口，用户名 / 密码与 Compose 一致。随后可运行 Maven 测试。

**一键 `test-browser.ps1` 会调用便携 PostgreSQL 管理与专用夹具重置，不能直接把它当作 Docker / Linux 通用启动器。** Docker 用户可切换回便携本地方案，或参照 `scripts/test-oauth-ci.sh` 和 CI 配置，在自己新建的隔离环境显式启动测试后端、Mock Provider、前端再运行 Playwright；后者需要理解测试环境变量和夹具生命周期。不要设置 `CI=true` 后对已有数据库盲跑 CI 脚本。

Linux CI 已验证，Windows 一键流程已验证；macOS 完整流程未单独验收。跨平台改进应附实际环境与验证，不把未执行的平台写成支持完成。

## 第四步：实现改动

### 前端

- 沿用 React / TypeScript、现有 API 客户端、Ant Design Token、中文界面和 Apple 风格。
- 路由守卫、菜单与后端权限协同，隐藏按钮不能代替 API 授权。
- 请求必须展示加载 / 空态 / 失败，处理过期会话及旧请求覆盖新状态的风险。
- 金额使用十进制字符串、Decimal.js、InputNumber stringMode，不用浮点求和。
- 新动效提供减少动态效果、移动端、画布 / WebGL 故障回退，并清理监听和计时器。
- 抽屉复用 MotionDrawer / DrawerPresence；改动进入或退出时应检查首次、复开、中间帧和焦点，不能只看最终截图。

### 后端与数据库

- 保持 Controller / Service / Mapper / Entity / DTO 分层，不为小改动重构核心架构。
- 参数校验、统一响应 / 错误处理、服务端角色及归属检查不能省略。
- 查询接口不返回密码、哈希、JWT、OAuth Secret 或其他敏感配置。
- 库存操作保留事务、排序锁、余额条件更新、持久幂等及原单关联，不能改成前端自算余额。
- 不同币种和单位分组，保留历史单价、实际日期及已确定报表口径。
- Demo 查询仅连接 demo_* 表，不回退正式表，不开放匿名或游客生产写入。
- V1–V7 不得改写。未来迁移先讨论，由维护者确认下一序号；测试从现有版本升级与新库初始化，不使用 clean / repair 掩盖问题。
- 新迁移不能假设可自动回滚；在 PR 说明锁表、兼容、数据影响及非破坏性恢复方案。

### 依赖、素材与文档

- 新依赖说明必要性、包体积、维护状况和许可，同步锁文件，不夹带无关升级。
- 不把整仓库重新格式化，跟随相邻代码风格；仓库没有要求运行的全局 lint / formatter 命令，不要虚构检查。
- 变更 API、行为、环境变量或运行方式时同步文档；开发记录追加实际完成与验证，保留历史。
- 图片来自自己的合成数据或授权素材；不得上传真实用户、财务资料或私人附件。
- 第三方许可独立保留。React Bits 是 MIT + Commons Clause，不能删掉 NOTICE 或声称被项目 MIT 重新授权。

## 第五步：验证并记录结果

按改动风险选择检查，在 PR 中分别标注 **通过 / 失败 / 未执行**，未执行说明原因。不要把旧 CI、别人的截图或 Mock 授权当作自己执行的新测试。

| 改动 | 最低验证建议 |
| --- | --- |
| 纯文档 / 模板 | 文件链接 / 图片、命令与实际脚本、YAML / 模板结构、敏感信息、diff 检查 |
| 前端逻辑 / 交互 | typecheck、build、相关桌面 / 手机浏览器用例；视觉前后图与必要的焦点 / 动效检查 |
| 后端规则 / API | Maven verify、成功 / 非法输入 / 越权 / 持久化 / 幂等等相关集成用例 |
| 认证 / 权限 / Demo | 原密码登录、Mock OAuth、默认 GUEST、正式 API 403、角色撤销、禁用账户、数据隔离 |
| 数据库 / 库存 / 金额 | 独立 PostgreSQL 新库与升级路径，完整相关业务回归，不改原正确性断言 |

常用 PowerShell 命令：

```powershell
./scripts/portable-postgres.ps1 test-db
. ./scripts/load-env.ps1
Push-Location ./backend
./mvnw.cmd -B verify
Pop-Location
npm.cmd --prefix frontend run typecheck
npm.cmd --prefix frontend run build
./scripts/test-browser.ps1
./scripts/test-browser.ps1 -OAuthMock

# 只有具体动效改动时，可在正常业务套件中聚焦该文件。
./scripts/test-browser.ps1 ui-motion.spec.ts

python ./scripts/audit-release.py
./scripts/check-release.ps1
git diff --check
```

浏览器脚本拒绝占用端口，不终止其他服务。先退出自己上一轮测试 / 预览，不能批量关闭所有 Java / Node 进程。独立 E2E 数据会被重置；不要绕过 `_test` 检查，也不要在普通业务数据库中运行 smoke 写入脚本。

Playwright 支持 Chrome 或 `E2E_BROWSER_CHANNEL=chromium`，也可用 `E2E_BROWSER_EXECUTABLE` 指定自己安装的浏览器；不要提交自己的绝对路径。截图测试可能更新历史图片，先看 diff，只提交本 PR 确实需要且不含私密数据的图片，不覆盖整套历史图来掩盖变化。

CI 对 Push / PR 执行 Maven、前端安装 / 类型 / 构建、Mock OAuth 18 项、动效 6 项及 Docker 512 MiB 检查。它目前不运行本地完整业务 70 项；业务改动需要贡献者补充本地回归证据。Fork CI 使用合成凭据，不需要生产 Secrets；维护者可能需要批准首次贡献的 Actions 运行。

测试报告在 backend/target/surefire-reports、frontend/playwright-report 等忽略目录。本地原始日志不直接上传，先脱敏后只附必要片段。审计是补充检查，不保证能识别所有未知秘密。

## 第六步：提交并推送自己的分支

```powershell
git status --short
git diff
git diff --check

# 只暂存你确认属于此次修改的文件；这里以文档为例。
git add README.md CONTRIBUTING.md
git diff --cached
git commit -m "docs: clarify local setup and contributor workflow"
git push -u origin HEAD
```

提交标题建议 `fix:`、`feat:`、`docs:`、`test:`、`chore:` 加具体动作；内容用中文或英文均可。无需伪造日期或拆成并不存在的历史 Sprint 提交。不要提交 .env、私钥、数据库、上传文件、日志、node_modules、target 或 dist；文件后来加入 .gitignore 也不会自动取消已跟踪状态。

如果发现秘密已被提交，即使尚未合并也应停止推送，先轮换并按安全流程处理；删除当前文件不等于清除历史。不要自行 force Push 上游或重写已发布历史。

## 第七步：在 GitHub 创建 PR

1. 打开自己的 Fork，点击 **Compare & pull request**。
2. 核对 **base repository = JouCristian/CaiZhiTong，base = main**；head 是自己的 Fork 和功能分支。
3. 一个 PR 解决一个明确问题，标题说明用户可观察到的改进；未完成的实现先建 Draft。
4. 按模板写动机、前后行为、范围、测试命令 / 结果、截图、兼容及迁移说明。关联 Issue 可写 `Closes #123`，没有 Issue 时不要伪造编号。
5. 提交后查看 Checks 和 CI 日志。失败先定位修复，Push 同一分支即可更新当前 PR，不必反复新建。
6. 等待维护者评审；对问题解释依据并补充测试，解决后标明处理位置。较大范围变更需重写标题和描述，反映最终实现。

可选使用已登录的 GitHub CLI：

```powershell
# 在编辑器创建自己的 PR 正文文件，不包含凭据。
gh pr create --repo JouCristian/CaiZhiTong --base main --title "修复具体问题" --body-file ./pr-body.md
```

CLI 应从自己已推送的贡献分支使用，必要时显式选择自己的 Fork head。没有 CLI 时使用网页即可，不要把 GitHub Token 写进命令、URL 或 PR。

### 跟进上游变化与冲突

```powershell
git fetch upstream
git switch fix/drawer-focus
git merge upstream/main
# 在编辑器解决冲突后，逐个 git add，重新验证再提交。
git commit
git push origin HEAD
```

示例分支名替换为自己的分支。若 merge 没有冲突且自动完成，无需额外空提交。不要用 force Push 或覆盖 main 来解决冲突；保留正常历史即可。维护者合并方式以仓库实际选择为准，不声称已配置强制规则。

## 合并与上线由谁负责

维护者在合并前核对 Head SHA、CI、范围、安全与许可；涉及行为变化时要求相应回归。CI 绿色是必要依据，不替代业务评审。冻结期功能扩展可能延期，不能因为提交了 PR 就假设会立即部署。

main 合并后，维护者检查 main CI、Render live、Vercel **production** 及正式域名。预览构建成功不等于上线；生产 CORS 不为每个 Fork 或预览开放。数据库问题不通过删除生产表或回退已执行 Flyway 处理；OAuth 故障优先关闭开关、保留密码入口。

v0.7.0 标签固定后不重打，后续修复使用新版本。当前没有要求贡献者签署额外 CLA 的流程；请确保你有权贡献代码与素材，并遵守项目及第三方适用许可。协作行为见 [CODE_OF_CONDUCT](CODE_OF_CONDUCT.md)。
