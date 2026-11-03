# Sprint 04 · 扫码领用、月度额度与 PDF

用户已确认 Sprint 01–03 人工验收通过。本轮增量开发，不修改 V1–V3，不 Push、不部署，不开发归还报废、审批、邮件预警或报表。

## 决策与开发记录

- 批次余额仍是库存唯一来源；领用按选定批次扣减，采用该批次入库价格、单位及币种快照计价，避免库存价值与领用金额口径不一致。
- 一个事务写入领用单 / 明细、月度用量、余额与 OUT 流水。UUID + 内容摘要 + 经办人幂等；材料与批次按 ID 顺序加锁，额度使用唯一键、行锁与条件更新。
- 业务时区由 BUSINESS_TIME_ZONE 配置，默认 Asia/Shanghai；月份由后端决定并随单据保存。
- 后端支持最多 20 个不同批次；一张单使用一种币种，混币种须分单，避免没有汇率却混加金额。本轮界面优先单批次领用。
- 为保留旧材料 PUT 契约并隔离管理员权限，月限额通过材料详情中的独立设置及 PATCH /api/materials/{id}/issue-limit 更新；NULL 不限，正数为上限，0 / 负数拒绝。实验员编辑普通档案不会清空额度。
- 领用人从 JWT 获取，管理员 / 实验员可看全量，其余领用角色仅能看本人详情与 PDF。
- PDF 使用 Java PDFBox、内嵌开源中文字体，历史字段来自持久化快照，手写签名栏无需外部服务。

## 当前验证

### 实际完成

- 复用 `/stock/batches/{batchCode}`，登录恢复后可直接领用该批次；桌面也可搜索材料并分页加载有余额的批次。停用 / 归档、零库存不可领用。
- 单批次界面具备库存、数量、必填项目 / 用途、备注、金额与个人月度额度提示。提交前进入明确的核对视图，确认后才发起真实写入。
- 后端支持多材料 / 多批次；扣减指定批次，保存精确快照与金额、本人身份、月份和时区，写入可追溯 OUT 流水。
- 未知响应保存原请求与批次快照到当前用户会话，刷新后可安全重试；相同幂等键、内容及用户返回原单。
- 本人记录与管理员 / 实验员全量记录分开，支持材料 / 单号、项目、领用人、日期筛选和服务端分页。手机历史为卡片，不压缩桌面表格。
- 权限同样作用于单据、PDF 与库存流水：教师仅本人 OUT，采购员只读 IN，管理员 / 实验员可看全部；学生从本人详情查看关联流水，旧菜单矩阵不变。
- PDFBox 在后端生成 A4 PDF，内嵌 OFL 中文字体，具备自动换行、分页、页码、价格快照与手写签名栏。无需外部 PDF / 字体服务。

### 文件与 V4

V4 新增 issue_order、issue_order_line、issue_monthly_usage，并扩展 material_item.monthly_issue_limit、stock_transaction.issue_order_id / issue_line_id。旧迁移保留，原 IN 外键及幂等逻辑不变。数据库用复合外键保障领用明细 / 批次 / 单号一致，并拒绝领用历史更新 / 删除。

后端主要位置：IssueController、IssueService、IssueMapper / IssueMapper.xml、IssuePdfService、BusinessTime；MaterialItem 添加只读额度字段，StockController / Mapper 增加 OUT 来源与隐私过滤。前端主要位置：src/issues/、批次页领用入口、材料详情限额设置、库存流水跳转、App 路由与登录恢复。版本 0.4.0。

### 一致性与权限

1. PostgreSQL advisory transaction lock 按领用 UUID 串行化，摘要包含规范化数量、项目、用途、备注与批次；入库与领用使用独立命名空间。
2. 去重并排序材料 ID 后加行锁，随后按批次 ID 加锁；与入库、材料归档和限额变更顺序兼容。
3. 月度用量先 INSERT ON CONFLICT，再锁定同用户 / 材料 / 月份行，用带上限条件的 UPDATE 累计。本次多个批次按材料合计消费额度。
4. 余额用 quantity>=delta 条件 UPDATE，单据、明细、额度、余额和 OUT 在同一事务，末尾失败也回滚。普通请求不能指定他人或写余额。
5. 数量三位、单价四位、金额七位精度，全程 BigDecimal / NUMERIC；前端 Decimal 精度 50。无限额仍累计，配置变化不清空使用量。
6. 身份由每次请求的 JWT / 数据库账户取得。本人详情权限与 PDF 一致；全量查询仅 ADMIN / LAB_TECH，库存流水也过滤私人 OUT，工作台仅公开入库动态。

### 运行与配置

继续支持 Windows PowerShell 和既有便携 PostgreSQL 15：根目录运行 scripts/portable-postgres.ps1 start、scripts/start-backend.ps1，另一个窗口在 frontend 运行 npm.cmd run dev。前端 5173、后端 8080；完整隔离验收仍使用 scripts/test-browser.ps1 的 5180 / 8081。

BUSINESS_TIME_ZONE 默认为 Asia/Shanghai，月份由服务端决定；PUBLIC_WEB_URL 继续决定 QR 来源。PDF 字体随工程分发，常规运行无需 Python、系统字体、云服务或专用打印机。字体源与修改说明见 backend/src/main/resources/fonts/README.md。

### 实际测试与修复

- 初轮后端 **88 / 89**；普通库存、额度、并发、整体回滚均通过。PDF 失败暴露字体反向 cmap 的兼容部首别名，以及仅大小写差异字段的映射冲突。
- 字体改为派生 CaiZhiTong Sans SC 并保留 OFL / 原版权，移除与普通汉字重复的部首别名；账户字段使用 accountName / recipientName 显式别名，修复后 **89 / 89**。
- 加入多材料 / 混币种策略、可领批次、长中文 PDF 分页用例后 **92 / 92**。再加入流水隐私旁路测试，最终 `mvnw.cmd -B -ntp clean package` **93 项，0 失败 / 错误 / 跳过**，生成 backend-0.4.0.jar。
- 保留 Sprint 01–03 的 66 个后端用例；新增 27 个领用用例，覆盖 800−20=780、OUT 关联、非法数量 / 状态、幂等 / 并发幂等、最后一份库存竞争、跨批次额度竞争、50=20+30、NULL、不足 / 回滚、月份边界、管理员限额、旧 PUT 保留限额、本人 / 全量及 PDF 权限、身份伪造、历史快照、分页与长 PDF。
- 桌面新业务专测首轮 2 / 3，失败为 403 文案与通用异常文案不一致；为新领用接口提供明确业务提示后，首次完整浏览器 **44 / 44** 通过，原有 38 个用例全部保留。
- 完整脚本内真实材料 / 库存 / 领用 HTTP 冒烟通过，真实 PDF 下载有 `%PDF-` 头，库存、额度、记录与 OUT 均落库；写入仅在独立 caizhitong_test / caizhitong_e2e_test。
- PDF 使用 PDFBox 解析、检查字体嵌入、中文 / 价格 / 签字信息和 A4；用 Poppler 实际渲染单页及四页长凭证并目视核对，未发现裁切或乱码。
- 局域网 HTTP 的浏览器可能不提供 crypto.randomUUID，入库与领用统一使用加密 getRandomValues 生成的 UUID v4 回退；安全重试浏览器用例移除 randomUUID 后检查 UUID 格式与真实幂等落库，不使用 Math.random。

| 命令 / 检查 | 最终结果 |
| --- | --- |
| 后端 mvnw.cmd -B -ntp clean package | 通过，93 / 93，打包成功 |
| 前端 npm.cmd run typecheck | 通过 |
| 前端 npm.cmd run build | 通过，Ant Design 公共包有体积提示 |
| scripts/test-browser.ps1（含三组 HTTP 冒烟） | 最终完整 44 / 44 通过（5.6 分钟），桌面 / 移动各 22；包含关闭 randomUUID 后的真实入库 / 领用幂等重试 |
| scripts/smoke-api.ps1，日常服务 | 通过，健康 / 三组演示登录 / JWT / 原模块权限 |
| PDF 解析 / 中文嵌入 / A4 / 多页渲染 | 通过 |
| 真机相机 / Safari / 物理打印 | 未执行，浏览器为桌面 Chrome 与 Chromium 移动模拟 |
| GitHub Push / 云部署 / 后续 Sprint | 未执行 |
| 人工验收 | 等待用户确认 |

### 实际截图与凭证

截图来自隔离 PostgreSQL 真实操作，测试数据不自动导入日常库。

| 页面 | 桌面 | 移动模拟 |
| --- | --- | --- |
| 领用核对 | [截图](screenshots/sprint04-issue-form-desktop.png) | [截图](screenshots/sprint04-issue-form-mobile.png) |
| 领用详情 | [截图](screenshots/sprint04-issue-detail-desktop.png) | [截图](screenshots/sprint04-issue-detail-mobile.png) |
| 本人历史 | [截图](screenshots/sprint04-history-desktop.png) | [截图](screenshots/sprint04-history-mobile.png) |
| 全量管理 | [截图](screenshots/sprint04-management-desktop.png) | [截图](screenshots/sprint04-management-mobile.png) |
| 额度配置 | [截图](screenshots/sprint04-quota-desktop.png) | [截图](screenshots/sprint04-quota-mobile.png) |

[后端真实生成的 PDF 样本](examples/sprint04-issue-example.pdf) · [PDF 实际渲染](screenshots/sprint04-pdf.png)。样本来自独立测试库的 test_student，不是日常用户凭证。

## 已知问题与边界

- 本轮不支持代领、审批、领用撤销、归还报废、电子签名、邮件或报表；单据与流水不可直接编辑，后续需审计调整流程。
- 界面优先单批次；后端最多 20 个不同批次，一单一种币种。没有汇率或混币种金额求和。
- 同材料使用行锁保证状态、额度与余额一致，尚未做高吞吐量压力测试；查询为服务端 OFFSET 分页，适合当前课程规模。
- 业务时区在运行后应固定，变更可能影响自然月边界。无额度也累计已领量，修改额度不清零。
- 常用中文已嵌入；字体未覆盖的罕见字符 / 表情以问号回退。签字栏供线下手写，未宣称完成电子签名。
- Ant Design 公共块约 1.08 MB（gzip 约 341 KB），构建提示体积，不阻断运行。
- 手机相机扫码需配置可达的 PUBLIC_WEB_URL，默认回环地址不能指向电脑；未验证物理手机或打印机。
- 延续原短期 JWT，会话退出只清除浏览器令牌，未实现服务端撤销。

## Sprint 05 准备

保留 RETURN / SCRAP 流水类型、批次剩余数量和稳定业务外键，单据 / 原始 IN / OUT 可追溯。后续归还报废、预警或报表须使用新迁移与受审计事务，本轮未创建对应业务接口。完成后停止，等待人工验收。
