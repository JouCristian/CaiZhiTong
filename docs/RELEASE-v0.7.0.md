# v0.7.0 冻结说明

冻结决策日期：2026-11-05（Asia/Shanghai）。用户在正式网站验收后明确要求冻结当前版本并完善开源展示及贡献规范。本次不开发新功能、不变更生产权限或库存口径。

## 基线与快照

| 项目 | 依据 |
| --- | --- |
| 应用版本 | 前端 package.json、后端 pom.xml / application.yml 均为 0.7.0 |
| 已部署应用基线 | `2e1e16e38e0f1531cf8883c530549a7b780ce381` |
| UI 功能合并 | PR #2，`df4778f0ab9361cfa39649806f954493c1f41ff8` |
| OAuth / Demo 合并 | PR #1，`4cc92227556f3277f7b34c739dc9d70cc43c4b2a` |
| 冻结标记 | annotated Git tag `v0.7.0`，在本轮文档整理完成、CI 通过后固定完整仓库快照 |
| 迁移 | V1–V7，冻结整理不修改任何 SQL 或应用源码 |

应用基线与冻结标签可能是不同提交：标签同时收录最终展示文档、PR / Issue 模板和贡献指南，业务源文件仍与应用基线一致。不伪造历史 Commit，不覆盖旧标签，不创建第二套产品版本号。

检出冻结快照：

```powershell
git fetch origin --tags
git show --no-patch v0.7.0
git rev-parse 'v0.7.0^{}'
git switch --detach v0.7.0
```

开始后续贡献时回到 main，并按 [CONTRIBUTING](../CONTRIBUTING.md) 从最新上游建立新分支，不直接在 detached HEAD 修改后丢失工作。

本轮固定 Git 标签，不额外发布 GitHub Release 页面。源码标签不是数据库、附件或环境变量备份，也不会锁住在线业务数据。

## 冻结范围

包含六轮业务、用户名密码 / JWT、GitHub OAuth、独立合成 Demo、V1–V7、Supabase 私有照片、部署配置、Apple 风格 UI 与已验收动效。已完成能力、角色、金额与库存边界以 [README](../README.md) 和 [API](API.md) 最新章节为准。

冻结后不继续为该版本增加功能、大规模重构、更换技术栈或改写旧迁移。文档、缺陷、安全、兼容性改进仍可提 PR；涉及行为的新修复进入后续版本，不移动 v0.7.0 标签。没有自动开启下一轮 Sprint 或承诺路线图日期。

冻结不等于归档仓库，不会关闭 Issues / PR，不会自动设置 GitHub 分支保护或停止 Render / Vercel 自动部署。main 的文档合并仍可能触发完整 CI 和平台重建，维护者需核对版本与正式域名。

## 验证依据

冻结应用基线 [CI 41954664321](https://github.com/JouCristian/CaiZhiTong/actions/runs/41954664321) 已实际成功：Maven 195 项、前端类型 / 构建、OAuth / Demo / 视觉 18 项、侧栏 / 抽屉 6 项、Docker 构建与 512 MiB 运行检查。PR #2 及合并后 CI 记录见 [UI 精修](UI-POLISH-01.md)。

本地完整业务 70 项通过，最终进入动画修正另执行专项 6 项；没有把专项当作再次全量执行。正式站 29 项验收、随后 6 次真实 HTTP 持久化复核通过。真实 GitHub 授权由用户亲自完成，后续 UI 发布没有重复真实授权，见 [Cloud Deployment 03](CLOUD-DEPLOYMENT-03.md)。

应用基线对应真实生产部署：

- Render：`dep-dblug9m8bjmc739bh810`，live。
- Vercel：`dpl_9XQFtDRQ9aGvyTczgk63nmZEMnKK`，READY / production，正式域名指向基线提交。
- 前端：https://caizhitong-eight.vercel.app 。后端：https://caizhitong-api.onrender.com 。

以上是冻结前的实际部署证据，不宣称平台长期不变。本轮文档整理验证链接、图片、模板、命令、敏感信息及应用 / 迁移无变化；远程 PR / main CI 结果以对应 Actions 和标签指向的实际提交为准，不把旧记录当作新执行。

## 本轮补齐的仓库资料

- README：定位、在线体验、完整业务、角色、架构、运行、变量、规则、测试、开发历程、部署与限制。
- CONTRIBUTING：Fork 到 PR 的完整步骤、隔离测试、代码 / 迁移 / 许可约定、评审和上线责任。
- PR 与 Issue 模板：复现、环境、验证、截图、迁移、安全和范围检查。
- SECURITY / SUPPORT / CODE_OF_CONDUCT：私密反馈、普通支持和协作约定。
- CHANGELOG：阶段成果摘要，链接原始记录，保留全部历史文档和截图。

## 重要限制与恢复

这是单实例课程项目，Free 冷启动、配额、无刷新令牌、无正式审批、未真实财务对接和未完成生产压测等限制仍存在。未配置 SMTP 时显示 NOT_CONFIGURED，站内预警保留；存储模式切换不自动迁移旧附件。

问题恢复由维护者评估：OAuth 异常优先关闭 GITHUB_OAUTH_ENABLED 并重新部署，保留密码入口；源码回退不能同时回退已经执行的 Flyway。禁止用清库、关闭校验或 repair 掩盖问题。数据库和私有附件单独备份，秘密不进入源码快照。
