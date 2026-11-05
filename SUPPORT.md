# 获取帮助

| 问题 | 先阅读 | 后续方式 |
| --- | --- | --- |
| 启动、JDK、端口、数据库 | [README](README.md#本地快速开始) | 按 Bug 模板描述自己的版本与最小复现 |
| API、403、角色可见范围 | [API](docs/API.md)、[Demo 隔离](docs/DEMO-ISOLATION.md) | 指明角色、接口、状态码，移除 Token |
| Fork、测试、提交 PR | [CONTRIBUTING](CONTRIBUTING.md) | 说明已执行步骤及失败环节 |
| 平台、CORS、OAuth 回调 | [DEPLOYMENT](docs/DEPLOYMENT.md) | 提供公开域名与脱敏状态，不发平台凭据 |
| 疑似漏洞或秘密泄漏 | [SECURITY](SECURITY.md) | 使用私密反馈路径，不公开复现细节 |
| 新功能建议 | [冻结说明](docs/RELEASE-v0.7.0.md) | 使用 Feature 模板，先讨论后实现 |

正式站使用免费后端，休眠首次连接可能需 2–3 分钟；请先查看等待提示和健康检查。游客正式业务 403 是隔离策略，不应通过放宽权限修复。未配置 SMTP 显示 NOT_CONFIGURED 是预期状态。

项目没有承诺专门客服、企业支持或修复时限。请在公开反馈中只提供必要的脱敏日志与合成截图，明确通过 / 失败 / 未执行，不要求他人接收密码或复制完整环境文件。
