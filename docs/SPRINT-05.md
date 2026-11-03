# Sprint 05 · 归还报废、预警邮件与报表

用户确认 Sprint 01–04 已通过人工验收。本轮增量追加 V5，不修改旧迁移，不 Push / 部署，不进入 Sprint 06。

## 决策记录

- 归还锁定原领用明细并累计已归还量，只回到原批次；不重置月度领用用量。报废只扣仓库现存余额，已领出损坏不重复扣仓库。
- 沿用材料 → 批次 → 原领用明细的排序行锁、独立 UUID 幂等命名空间及同一数据库事务；继续使用 IN / OUT / RETURN / SCRAP 同一本流水。
- 持续低库存只有一个活动事件；恢复后解除，重新跌破产生新周期。事务内记录通知，提交后发送，邮件失败不回滚库存，无 SMTP 明确显示未配置。
- 报表按实际领用 / 归还日期、原项目 / 课程 / 价格快照统计，跨期净消耗允许负数。仓库报废独立列；币种和计量单位分别汇总。
- 课程为可选短文本，旧单据显示未关联课程。保留旧幂等摘要在课程未填写时的兼容行为。
- 既有 CRUD 保留 MyBatis-Plus；新运维与统计投影在 Mapper 封装 Spring JDBC 预编译 SQL，复用同一数据源和 Spring 事务，不引入新框架。
- 三类报告共用统计服务，预览与导出使用相同口径；通用财务模板，不宣称真实财务系统对接。

## 实际完成

- 完好材料多次 / 部分归还、剩余可归还量、原领用搜索、原批次回库、本人 / 管理历史和详情。
- 仅仓库余额报废、原因与照片、事务扣减、不可变历史、认证附件、实际类型 / 大小 / 尺寸校验。
- RETURN / SCRAP 写入原流水体系并提供单据追溯；普通读者不能通过流水旁路读取他人领用。
- 持久预警周期、低库存指标、确认备注、自动恢复、阈值编辑钩子及启动 / 定时 / 手动扫描。
- 真实 SMTP / TLS、采购员邮箱和显式收件人、提交后异步通知、状态与原因查询、显式重试。
- 三类服务端分页预览，学期 / 自定义日期 / 分类 / 材料 / 课程 / 项目 / 用户筛选；全部六种 XLSX / PDF 导出。
- 原领用页新增可选课程，原凭证显示课程或未关联课程；学生获得本人报表，后端保护其他用户数据。
- 桌面表格、移动记录卡片 / 抽屉、未知结果持久请求恢复、真实错误 / 加载 / 空状态。

## V5 与代码入口

迁移：`backend/src/main/resources/db/migration/V5__returns_scrap_alerts_reports.sql`，仅追加，不修改 V1–V4。新增 return_order / return_order_line / scrap_order / stock_attachment / inventory_alert / mail_notification，扩展 sys_user.email、issue_order.course 和原库存流水外键。金额与数量保持 NUMERIC / BigDecimal，历史记录受不可变触发器保护。

- `StockLifecycleService`：排序锁、源明细校验、累计上限、幂等、库存和流水事务。
- `AttachmentService` / `AttachmentStorage`：解码校验、路径与权限、存储可替换；文件失败不生成完成业务。
- `AlertService` / `NotificationStore` / `NotificationWorker` / `InventoryJobs`：周期状态与提交后邮件。
- `ReportService` / `ReportExporter`：同源预览 / 导出、原价格与跨期归还、精确分组汇总、XLSX / 中文 PDF。
- `LifecycleController` / `ReportController`：DTO 校验、原响应格式、真实角色授权。
- `frontend/src/operations/`：归还报废、预警、报表及通用错误 / 分页 / 附件组件。
- `LifecycleIntegrationTest` / `ExportAndStorageTest` / `NotificationCommitTest`、`frontend/e2e/lifecycle.spec.ts`、`scripts/smoke-lifecycle.ps1`：新增验收。

测试启动在每轮开始前重置专用 E2E 库业务夹具，保留账户和 Flyway 历史，避免反复运行积累分类拖慢 UI；PowerShell 参数和 JDBC 当前数据库双重防护拒绝日常库重置。该拒绝路径已实际验证。

详细接口与字段见 [API](API.md)，表说明见 [数据库](../database/README.md)。

## 验证记录

- Maven `clean package`：122 / 122 通过，0 失败、0 跳过，生成 backend-0.5.0.jar（保留原 93 项，新增 29 项）。
- TypeScript 检查与生产构建通过；Vite 保留既有 Ant Design 大包提示，没有构建错误。
- 真实 PostgreSQL HTTP 冒烟完成归还 / 报废幂等、100−20+5−3=82、越权拦截、预警确认及恢复、三类预览和六种下载。
- Excel 用 POI 读取校验标题、冻结表头、金额及公式注入安全；补充 ZIP / XML 检查。PDF 用 PDFBox 校验中文、金额、空报表、长字段多页和重复表头，并通过 Poppler 渲染目视检查。
- SMTP 只使用模拟网关及受控 loopback SMTP。验证事务内不可见、回滚不发送、提交后才发送、失败不回滚及显式重试。不使用真实外部邮箱。
- 完整桌面 / 移动浏览器 **56 / 56 通过**，0 失败、0 跳过（保留原 44 项，新增 12 项），实际运行 5.6 分钟。移动端六种下载复验通过；10 张实际截图已保存。

早期验证修复：PDF 换行文本核对、多币种样本遵守既有入库约束、测试 URL 编码、图标按钮可访问名称，以及预警多周期历史 / Select 容器产生的测试定位歧义。一次本地数据库进程中断已恢复；一次 Windows PowerShell 5.1 把 JVM stderr 警告误判失败，改用 PowerShell 7 收集结果，最终以实际测试报告和退出码为准。Windows 隐藏进程中测试 Vite 多次退出；CLI 存在 stdin 结束关闭的路径，但单独设置 CI 模式不足以稳定运行。最终改为 PowerShell 7 优先、直接 Vite API 启动，移除 npm 交互控制台包装并记录退出码，完整回归通过。一次完整浏览器轮次 55/56 通过，移动报表连续下载中查询按钮等待超时，补充稳定的可访问名称和加载禁用状态，并提升六种下载场景的总超时预算。未把中断或失败的轮次记为通过。

日常库仅自动迁移 V4 → V5，迁移前后材料 / 批次 / 领用记录数量一致，认证 / 健康 / 角色冒烟通过。所有新增写入测试均在独立 *_test 数据库。

## 报表与截图

六份真实样本在 [examples/sprint05](examples/sprint05/)，从隔离 PostgreSQL 测试业务生成。样本领用 20、归还 5，原单价 0.1234，领用金额 2.468、归还冲减 0.617、净消耗 15 / 1.851。样本账户和照片为自动化测试夹具，不是实际学校业务。

实际 Chrome 桌面与移动设备模拟截图在 `docs/screenshots/sprint05-*.png`。人工操作详见 [验收清单](SPRINT-05-ACCEPTANCE.md)。

## 已知边界

- 本地无真实 SMTP 凭据时显示未配置；未验证外部邮箱最终投递，用户可按 README 自行配置后验收。
- 邮箱由数据库配置，用户与权限 / 系统设置维护页尚未开发。
- 本地附件需持久卷与独立备份；上传后取消登记会保留未绑定文件，本轮不自动清理。
- 发送时进程中断可能保留 SENDING，需人工核对实际邮件后处理，避免自动重复。
- 财务表是通用模板，未针对学校真实财务系统验证；导出上限 10,000 行，需更大范围时分批筛选。
- 移动验收为 Chromium 模拟，未宣称真机 Safari、物理打印机或财务系统联调。
- 用户人工验收尚未执行；本轮不自行标记人工通过。

## Sprint 06 扩展点

可在现有角色、库存不可变流水、附件接口、预警事件与报表过滤层上追加用户 / 邮箱配置、运维与审计能力；如后续获得真实财务模板，可独立适配导出布局。继续保留前五轮迁移与回归测试。本轮未开始 Sprint 06，不 Push 或部署。

## 最终交付状态

Sprint 05 本地开发与自动验收完成，等待用户人工验收。日常前端 `http://127.0.0.1:5173`、后端 `http://127.0.0.1:8080`，健康检查 `/actuator/health`；测试用 5180 / 8081 服务已由脚本关闭。日常库保持真实零业务数据，未自动导入任何验收夹具。

最终命令：后端目录 `./mvnw.cmd -B clean package`（122 通过）；根目录 `npm.cmd --prefix frontend run typecheck`、`npm.cmd --prefix frontend run build`（通过）；`./scripts/test-browser.ps1`（56 通过，含四组 PostgreSQL HTTP 冒烟）；`./scripts/smoke-api.ps1`（日常实例只读认证 / 健康检查通过）。最终失败 0；外部 SMTP 实际投递、真机 Safari 与财务系统模板联调未执行。
