# Cloud Deployment 02 · 真实线上部署

日期：2026-11-04（Asia/Shanghai）。版本保持 v0.6.0。本轮经用户授权创建免费资源、部署及验证，不是业务 Sprint；不导入本地业务数据，不创建付费服务。

## 实际架构与入口

| 组件 | 实际配置 |
| --- | --- |
| 前端 | [Vercel 生产站点](https://caizhitong-eight.vercel.app)，Hobby，项目 caizhitong，frontend 根目录、Vite、Node 24.x |
| 后端 | [Render API](https://caizhitong-api.onrender.com)，caizhitong-api、Free、Singapore、Java 17 Docker |
| 数据库 | 原有 Supabase CaiZhiTong Free / Mumbai，实际 PostgreSQL 17.11，Session pooler 5432、TLS |
| 附件 | 原有 private Bucket caizhitong-attachments，5 MiB，JPEG / PNG / WebP |
| 源码 | JouCristian/CaiZhiTong，main；平台均关联同一仓库 |

## 配置与安全

- 官方 CLI / API 授权由用户完成，数据库连接与 Storage 服务端凭据通过终端隐藏输入。真实值只进入本机忽略文件和 Render 环境变量，不写 Git、Docker ARG、前端或本文。
- DB_URL 是不含凭据的 JDBC SSL 地址；应用 Hikari 最大 3 连接，Flyway 使用独立连接，在 caizhitong Schema 原样执行 V1–V6。六版成功记录与本地 SQL CRC32 一致；未修改迁移、未 repair / clean、未写入平台 Schema。
- 实测 anon / authenticated 均没有业务 Schema USAGE。用户已关闭 Data API，鉴权请求 /rest/v1/ 实测返回 401；这里记录观测结果，不把单一 HTTP 状态视为平台全部安全配置的证明。
- DEMO_ENABLED=false。首次生产管理员以 BCrypt 创建；真实登录成功后，Render BOOTSTRAP_ADMIN_ENABLED=false，并移除 BOOTSTRAP_ADMIN_PASSWORD。重新部署后仍能登录，启用管理员数量为 1，无公开注册接口。
- CORS_ALLOWED_ORIGINS 和 PUBLIC_WEB_URL 均为 https://caizhitong-eight.vercel.app。允许来源预检通过，其他来源返回 403；二维码内容指向该前端批次深链。
- 私有照片仅由 Spring Boot 认证代理读取。服务端 Storage key 不进入 VITE_ 变量，直接匿名读取 public / authenticated 对象地址均被拒绝。
- SMTP 未配置，不宣称外部邮件成功；站内预警保持可用。

## 实际验收

首次部署使用已审计提交 1355a14 的源码。Render 真实构建 Docker 成功、服务 live，公开 HTTPS /actuator/health 返回 UP。新私有 Schema 执行六个版本迁移；Flyway 后续验证的七条记录包括一条 Schema 创建标记，并非 V7。

云端测试新建明确标注“云端验收”的分类、材料和独立合成账户；没有导入真实学生。API 验收共 35 个 HTTP 检查（含最终账户停用与旧令牌拒绝）：

| 项目 | 实测结果 |
| --- | --- |
| 管理员及临时学生登录 | 成功；无令牌 401，学生管理用户 / 分类及资金报表 403 |
| 分类、材料、入库 | 真实保存；入库同幂等键重试返回同一单据 |
| 领用、归还、带照片报废 | 100 入库 − 20 领用 + 5 归还 − 3 报废 = 82 |
| 精确金额 | 单价 0.1234，净消耗 15，净消耗金额 1.851；库存价值 CNY 10.1188 |
| 导出 | 领用 PDF；学期 / 资金 / 个人三类报表的全部六种 Excel / PDF，签名与业务值正确 |
| 私有照片 | 上传成功、管理员读取字节一致；无令牌 401、学生 403、Storage 匿名直读拒绝 |
| 仪表盘 | 管理员 ALL；学生 PERSONAL 且无他人排行 |
| 停用账户 | 验收账户停用，旧 JWT 随即 401，临时密码未发布 |
| 重新部署持久性 | 原材料、批次余额 82、照片 SHA-256 均保持一致；共 6 个复验请求通过 |
| CORS / SPA / HTTPS | 精确来源通过，外站拒绝；Vercel /materials 深链返回应用，真实浏览器可恢复登录 |
| 浏览器 | 桌面九个业务菜单、工作台、批次二维码深链、刷新；手机登录、工作台与退出均通过 |

实际截图：[云工作台](screenshots/cloud-dashboard-desktop.png)、[手机工作台](screenshots/cloud-dashboard-mobile.png)、[材料管理](screenshots/cloud-materials-desktop.png)。截图来自真实部署，无密码或令牌。

Render 首次 Spring 启动约 152 秒，后续约 116 秒。首次导出后观测内存约 362 MiB，低于 512 MiB；验收窗口未发现 OOM 反复重启，不代表长期稳定性或负载测试。旧实例在滚动部署后的正常 graceful shutdown 不计为故障重启。

本次补充前端 VITE_API_TIMEOUT_MS（默认 12 秒，生产 240 秒，上限 300 秒）、登录长等待提示和生产账户说明；业务契约、数据库迁移、本地存储默认行为均不变。这样在 Free 实例休眠唤醒时，页面不会固定于 12 秒提前中断。

## 回归与发布证据

前端类型检查、生产构建通过；原有隔离数据库浏览器测试 **64/64**（7.7 分钟），五组真实 API 冒烟通过。本轮未改变后端源码，发布后仍由 GitHub CI 重跑全部 173 项后端与 Docker 检查。CI / 自动部署的实际结果在本轮收尾时追加，不提前预写成功。

另通过 16 个云端 HTTP 请求验证：合成账户临时分配实验员后可访问入库；改回学生后同一旧 JWT 立即 403；密码重置后旧 JWT 401、旧密码登录 401，新密码可登录；最终再次停用，旧 JWT 401。该账户和随机临时密码不对外发布。

远程状态入口：[GitHub CI](https://github.com/JouCristian/CaiZhiTong/actions/workflows/ci.yml)、[前端](https://caizhitong-eight.vercel.app)、[后端健康](https://caizhitong-api.onrender.com/actuator/health)。

## 当前限制

- Free 休眠、冷启动、配额及无 SLA；站点首次访问需要耐心等待。尚未执行长期压力测试、灾难恢复演练或付费监控。
- 生产没有公开通用密码。用户使用自己设置的管理员密码，并通过用户页面按需创建其他账户。
- SMTP、独立域名与正式 GitHub Release / tag 未启用。本轮没有开通付费资源。
- 云端验收记录保留用于人工复核；测试学生账户已停用。照片为合成测试图，不是本地私人附件。
