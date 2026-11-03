# v0.6.0 源码发布记录

日期：2026-11-04（Asia/Shanghai）。本轮是发布准备，不是 Sprint 07；保留已验收业务规则与 V1–V6。用户在发布任务中确认 Sprint 01–06 本地人工验收通过。

## 初始 Git 与账户状态

- 工作区已有 Git，但最初无提交、无远程、暂存区为空；本轮在确认目标不存在后创建公开仓库。
- 本地存在编辑工具留下的不可达 blob / tree，审计覆盖全部对象而非只检查可见分支。初审为 554 个 blob、0 个 commit 对象，未发现已知本机秘密或 Token 特征。
- GitHub 连接器及用户完成网页授权后的 CLI 均确认当前账户为 JouCristian（ID 268768586）。官方 GitHub CLI 2.103.3 已下载并核验官方 SHA256；授权后再次确认目标不存在，创建 `JouCristian/CaiZhiTong` 公开空仓库，未覆盖既有仓库。
- 首次发布创建当前源码的真实初始提交，六轮开发事实由日志记录，不生成虚构历史提交。

## 安全与完整性检查

运行 `scripts/check-release.ps1` 和 `scripts/audit-release.py`。后者检查已跟踪敏感路径、全部发布候选文件、实际本机秘密及常见 Token / 私钥特征、全部 Git blob（含不可达对象）、Markdown 文件链接。匹配只报告文件 / 对象 ID，不打印秘密值。

初审未发现实际秘密。真实 `.env`、PostgreSQL 数据、上传附件、日志、node_modules、target、dist、工具下载和测试报告保持忽略；已有暂存区为空，因此没有“先跟踪再加入 ignore”的文件。样本报表及截图为本项目测试数据，非日常真实业务库导出。

MIT 保留，标准 Maven Wrapper 3.3.4 的 Apache-2.0 许可单独保存在 `backend/.mvn/wrapper/LICENSE`；字体 OFL 及派生说明保留。配置模板只有占位密钥，测试用固定密码仅用于隔离 CI / 测试。

## 文档与发布工程调整

- README 突出已实现功能、八个真实核心界面、六轮开发历程、可迁移的相对命令与文件链接；记录 Node 24.22.1、OpenLogic JDK 17.0.20+8、PostgreSQL 15.15。
- 补齐标准 Maven Wrapper（Windows / Unix，Maven 3.9.16），先比对 Maven Central 发布的 SHA512，再将同一分发包的 SHA256 固定在 Wrapper 配置中；保留原 PowerShell 辅助脚本。CI 使用该 Wrapper 和固定 Node 24.22.1。
- 新增后端多阶段 Dockerfile / dockerignore，补充 Render / Neon / Vercel 配置。当前没有 R2 存储适配器，文档明确需要后续开发，不编造已支持的云配置。
- 不修改业务接口、统计口径、权限或历史迁移，不进行实际云部署，不创建正式 Release / tag。

## 发布复验

发布前重新安装前端依赖，并执行完整复验，未以 Sprint 06 的结果代替本轮执行：

| 命令 / 检查 | 本轮实际结果 |
| --- | --- |
| 后端 `./mvnw.cmd -B verify` | 147 / 147，失败 0、错误 0、跳过 0；0.6.0 JAR 打包成功 |
| `npm.cmd --prefix frontend ci` | 重新安装成功，npm 报告 0 vulnerabilities |
| `npm.cmd --prefix frontend run typecheck` | 通过 |
| `npm.cmd --prefix frontend run build` | 通过；保留 Ant Design 大包提示 |
| `./scripts/test-browser.ps1` | 64 / 64，0 失败、0 跳过，5.3 分钟 |
| 五组真实 API 冒烟 | 材料、库存、领用、归还报废 / 报表、用户与设置均通过 |
| 标准 Maven Wrapper | Windows Wrapper 实际运行 Maven 3.9.16 / OpenLogic JDK 17.0.20+8 |
| 日常实例与清理 | 三账户认证 / 健康 / 权限通过；日常库 V6，材料 / 批次 / 领用均仍为 0；5173 恢复，5180 / 8081 已关闭 |

前端安装、构建与本轮浏览器服务使用 Node 24.22.1；测试写入独立 `caizhitong_test` / `caizhitong_e2e_test`。Docker 构建与远程 CI 不在此本地通过清单中。

首轮 `npm ci` 遇到日常 Vite 持有 esbuild.exe 的 Windows 文件锁；核对进程归属后仅关闭日常前端，保留后端和 PostgreSQL，再重装。标准 Wrapper 生成时 PowerShell 未加引号的版本参数被截断，已将 distributionUrl 固定为实际 Maven 3.9.16 官方地址后重新验证。失败 / 中断轮次不记为通过。

## 远程仓库与验证入口

- 源码仓库：[JouCristian/CaiZhiTong](https://github.com/JouCristian/CaiZhiTong)，公开仓库，主分支 main。
- 真实工作流：[Build and test](https://github.com/JouCristian/CaiZhiTong/actions/workflows/ci.yml)，README 使用该工作流实时状态，不预写通过徽章。
- 工作流在 push / pull_request 触发，Ubuntu PostgreSQL 15、JDK 17、Maven Wrapper verify、Node 24.22.1、npm ci / typecheck / build；Windows 完整浏览器回归结果如上，不混称远程浏览器通过。
- 本记录随真实初始源码提交写入，首次 Push 后须读取该提交的 Actions 实际结果；失败应修复后追加真实提交再验证，不使用 force push，不回填六轮历史，不创建正式 Release / tag。
- 认证通过 CLI 系统凭据存储；提交作者使用已确认 GitHub ID 的 noreply 邮箱，不把私人邮箱或 OAuth Token 写入源码 / 远程 URL。

### 首次 Push 与实际远程结果

首次源码提交：[0bc5942fadeca9a71002f6803873be6b75e08938](https://github.com/JouCristian/CaiZhiTong/commit/0bc5942fadeca9a71002f6803873be6b75e08938)。已正常 Push 至 main；没有 force push，没有覆盖其他远程仓库。GitHub 确认为公开、main 默认分支、MIT 许可证；远程递归树 285 个文件的 blob ID / mode 与本地提交逐项一致，包括 Unix Wrapper 可执行位。真实配置、数据库、上传目录和构建结果不在远程树中。

首次 CI：[Build and test #41682135755](https://github.com/JouCristian/CaiZhiTong/actions/runs/41682135755)，`completed / success`，完成于 2026-11-04 01:15:59（Asia/Shanghai）。已读取真实步骤与日志：

- Ubuntu PostgreSQL 15 服务成功启动，Temurin JDK 17 与 Node 24.22.1 安装成功。
- Maven Wrapper verify：147 项，0 失败、0 错误、0 跳过，BUILD SUCCESS。
- npm ci：成功，报告 0 vulnerabilities。
- TypeScript 与生产构建：成功，保留已知 vendor 体积提示。

发布记录与 README 的结果整理作为后续真实文档提交，仍由同一 push 工作流自动检查。最新 main 的实际状态查看 Actions / README 实时徽章，不回填虚构成功结果。本地 Windows 首次 TLS 握手失败后，仅为本次 Git 推送切换到 Git 自带 OpenSSL 后端并成功；证书校验始终开启，未全局关闭 TLS 校验或持久修改用户的 Git 凭据助手。

## 后续部署边界

尚未部署 Render、Neon、Cloudflare R2 或 Vercel，未发布公网体验地址。Docker 镜像尚未实际构建验证，公开管理员初始化、附件持久存储与备份、外部 SMTP、生产压测和学校财务对接需单独验收。详见 [部署说明](DEPLOYMENT.md)。
