# 变更记录

本文件按项目阶段概括成果，详细实现和失败修复链接到原始开发记录。阶段版本不等于当时已经存在 Git 标签或 GitHub Release；首次源码公开前的 Sprint 没有伪造历史提交。

## 未发布 · 登录连接修复候选

- OAuth 配置使用 8 秒探测与有限重试，补充手动重新连接、网络 / 响应异常 / 关闭状态。
- 保留密码输入，取消离页请求与旧定时器，合并重复提交，不改变授权协议。
- 记录 Render 跨网络零字节超时、受控重启未恢复及 Supabase 只读检查；不把前端补丁当成后端恢复。
- 仅在 fix/render-login-connectivity 分支与 PR 交付，v0.7.0 标签不变。依据见[专项记录](docs/FIX-LOGIN-CONNECTIVITY.md)。

## v0.7.0 · 2026-11-05 冻结

- Spring Security GitHub OAuth、GitHub 数字 ID 稳定关联、默认 GUEST、浏览器绑定的短时一次性交换，原密码登录保留。
- 独立 demo_* 表及只读 API，游客拒绝全部正式业务数据和写入；V7 增量迁移。
- 受控生产上线，持有人亲自授权 / 复登；正式业务、身份与照片重部署持久化验证。
- React Bits 登录视觉、自有标识、静态回退，修复侧栏溢出、抽屉进入闪现 / 关闭卸载 / 焦点接续。
- 固定冻结基线，整理 README、贡献流程、PR / Issue 模板、安全与协作说明。

依据：[OAuth](docs/V0.7.0-GITHUB-OAUTH.md)、[Demo 隔离](docs/DEMO-ISOLATION.md)、[生产验收](docs/CLOUD-DEPLOYMENT-03.md)、[UI](docs/UI-POLISH-01.md)、[冻结](docs/RELEASE-v0.7.0.md)。

## v0.6.0 · 2026-11-04 核心业务及首次公开

- Sprint 01：工程、认证、六角色、后台框架。
- Sprint 02：分类、材料档案、服务端查询分页。
- Sprint 03：多明细入库、批次、流水、二维码。
- Sprint 04：领用、月额度、幂等扣减、中文 PDF。
- Sprint 05：归还报废、照片、预警、三类六种导出。
- Sprint 06：用户、设置、仪表盘、完整回归。
- 首次公开源码与 CI，随后补充 Supabase Storage、私有 Schema、独立 Flyway 和受控生产管理员；真实部署到 Vercel / Render / Supabase。

依据：[开发日志](docs/DEVELOPMENT.md)、[源码发布](docs/RELEASE-v0.6.0.md)、[本地云适配](docs/CLOUD-DEPLOYMENT-01.md)、[真实云部署](docs/CLOUD-DEPLOYMENT-02.md)。
