# Sprint 03 · 库存、入库、流水与二维码

Sprint 01、02 已由用户验收通过，本轮基于现有工程增量开发，不进行 GitHub Push 或云部署。

## 决策与范围

- 仅追加 V3，保留 V1 / V2。
- 批次是库存数量唯一来源，数量精度三位小数；单价快照四位，金额精度七位。
- 入库单、明细、批次、IN 流水在同一事务。请求 UUID 唯一约束、规范化请求摘要和 PostgreSQL 事务锁提供幂等性。
- 材料按 ID 顺序加行锁，和既有材料编辑 / 归档锁兼容。
- 总价值按币种分组，库存数量不跨单位相加；过期批次仍是物理库存，不自动扣减。
- 保留旧菜单角色矩阵；学生 / 游客从材料详情读取库存与受权限保护的批次。入库写操作仅 ADMIN / LAB_TECH。
- 交易流水是批次余额变化。本轮仅 IN，新批次为 0 → 入库数量。
- 二维码仅编码配置的前端地址与稳定批次号，不含 JWT。使用 ZXing 生成真实 PNG。
- 前端金额预览使用 decimal.js，不使用浮点计算。

## 开发与验证

### 实际功能

- 库存总览、零 / 低 / 正常库存状态、分类及名称 / 编码筛选、服务端分页、各材料批次抽屉。
- 多明细入库（最多 50 行，可含同材料多行）、档案默认单价、库位 / 有效期 / 备注、精确汇总预览、真实入库详情及历史查询。
- 原请求按用户保存在当前浏览器会话，未知提交结果可在刷新后安全重试；返回原单不会重复增加库存。明确拒绝后可修改重新提交。
- 真实流水按材料 / 批次 / 类型 / 日期查询，追溯入库单、批次、经办人和批次变化前后余额。
- ZXing 生成真实二维码 PNG，下载、标签预览、浏览器打印、受保护批次深路由、登录后返回及不存在批次提示。
- 材料列表批量读取库存，详情查看批次；工作台实时显示材料种数、有库存种数、低库存种数和最近五条入库。
- 已有批次后锁定材料计量单位，避免历史数量混用；档案改价不影响历史金额。

### 数据与文件

追加 `backend/src/main/resources/db/migration/V3__stock_inbound_ledger.sql`：inbound_order、inbound_order_line、stock_batch、stock_transaction。价格与材料描述在明细保留快照；库存数量只保存在批次，物料表不增加库存字段。数据库序列负责唯一单号 / 批次号 / 交易号，外键、CHECK、唯一约束及触发器保护历史。

后端新增 Inbound / Stock Controller、Service、Mapper 与 DTO，QrCodeService 负责公开前端来源校验和 PNG。MaterialService 局部追加单位保护，WorkspaceController 接入真实概览，旧认证与分类档案 API 保持兼容。

前端新增 `src/stock/{InventoryPage,InboundPage,BatchViews,types}` 与 stock.css；App 路由、工作台、MaterialList / MaterialDrawer 接入真实库存；api.ts 增加认证二进制读取。依赖仅增加 decimal.js / dayjs 与 ZXing，没有新框架。版本更新为 0.3.0。

API 参数及权限见 [API.md](API.md#sprint-03--库存入库与标签)，可操作验收见 [SPRINT-03-ACCEPTANCE.md](SPRINT-03-ACCEPTANCE.md)。

### 一致性

1. 后端验证后按请求 UUID 获取事务锁，比较请求摘要及经办人，安全返回既有单据。
2. 对去重材料 ID 排序加行锁，检查启用状态及单位 / 币种快照。
3. 在同一事务创建入库单、明细、每行独立批次与 IN 流水。失败全部回滚，重试重新执行或返回已提交结果。
4. 数量使用 NUMERIC(18,3) / BigDecimal；价格 NUMERIC(14,4)，金额最多七位小数，前端 Decimal 精度设为 50 位。多币种分开汇总，不混加数量单位。
5. 数据库拒绝普通更新 / 删除历史。未来 OUT / RETURN / SCRAP 须原子更新批次并追加新流水，不更改 IN。

### 实际测试记录

- 首次测试暴露 UUID 无 MyBatis 类型处理器；使用字符串及显式 UUID 转换后，63 项后端测试通过。
- 补充单位保护和二维码缺省 / 非法来源边界后，`backend/mvnw.cmd -B -ntp package` 实际执行 **66 项，0 失败 / 0 错误 / 0 跳过**，生成 backend-0.3.0.jar。
- 新增 StockIntegrationTest 覆盖单行、多材料、同材料多批次、小数数量、非法 / 不存在 / 停用 / 归档、相同请求 / 不同内容与经办人、并发幂等、末尾流水写入失败整体回滚、余额 / 经办人 / 来源、四类只读角色禁止写、PNG 真实解码、历史快照、分页筛选及数据库历史保护。
- QrCodeServiceTest 验证缺 PUBLIC_WEB_URL 时不阻断查询、PNG 明确报错，以及配置不得包含凭据 / 查询参数。
- 真实隔离 HTTP 冒烟验证多行入库、精确数量、幂等、权限、流水和 PNG，已通过。日常 8080 的原认证 / 健康冒烟也已通过。
- 首轮完整浏览器 **34 / 38** 通过。发现库存页签切换用旧数据渲染新列导致报错，清空旧记录后修复；修复移动材料下拉的遮罩层级。首轮一次旧登录用例在开发热更新中超时，保留原用例参与最终回归。
- 修复后的桌面新业务专测 **3 / 3** 通过，包含多明细入库至二维码完整流程、只读 / 登录恢复、提交已落库但响应中断后的刷新重试。
- 后续完整回归两次为 37 / 38，继续定位移动端下拉滚动：将选项定位于抽屉表单容器，并关闭最多 30 项远程结果的虚拟列表，避免滚动后的裁切与错误点击区域。保留真实点击测试，没有使用 force 绕过遮挡。
- 移动端三条新业务连续两轮 **6 / 6** 通过；未知提交恢复另外连续三轮 **3 / 3** 通过。最终全量结果见下表。自动写入仅发生在 caizhitong_test / caizhitong_e2e_test。

| 实际命令 / 检查 | 结果 |
| --- | --- |
| backend：mvnw.cmd -B -ntp package（先加载本地环境） | 通过，66 项，0 失败 / 错误 / 跳过，生成 0.3.0 JAR |
| frontend：npm.cmd run typecheck | 通过 |
| frontend：npm.cmd run build | 通过，包含 tsc 与 Vite 生产构建 |
| scripts/smoke-api.ps1，日常 8080 | 通过，健康、三个演示账户、JWT、权限 |
| scripts/test-browser.ps1 内材料 / 库存 HTTP 冒烟 | 通过，独立 PostgreSQL 真实写入 |
| scripts/test-browser.ps1 完整浏览器 | 最终 38 / 38 通过（2.8 分钟），桌面 / 移动各 19；原有 32 项全部保留并通过，新增 6 项 |
| Flyway V3，日常 / JUnit / E2E 三库 | 通过，原 V1 / V2 未修改 |
| 手机实机 / Safari / 物理打印 | 未执行；仅 Chromium 模拟、PNG 解码和打印媒体验证 |
| GitHub Push / 云部署 | 未执行，遵循本轮范围 |
| 人工验收 | 等待用户，本轮不自行确认 |

### 实际截图

图片均为运行中的真实隔离数据库与浏览器，不自动导入日常业务库。

| 页面 | 桌面 | 移动模拟 |
| --- | --- | --- |
| 库存 | [截图](screenshots/sprint03-inventory-desktop.png) | [截图](screenshots/sprint03-inventory-mobile.png) |
| 入库表单 | [截图](screenshots/sprint03-inbound-form-desktop.png) | [截图](screenshots/sprint03-inbound-form-mobile.png) |
| 入库详情 | [截图](screenshots/sprint03-order-desktop.png) | [截图](screenshots/sprint03-order-mobile.png) |
| 流水 | [截图](screenshots/sprint03-ledger-desktop.png) | [截图](screenshots/sprint03-ledger-mobile.png) |
| 二维码标签 | [截图](screenshots/sprint03-label-desktop.png) | [截图](screenshots/sprint03-label-mobile.png) |

### 已知边界

- Chrome 桌面与 Chromium 移动模拟；未测试真机 Safari、物理手机扫码或实际标签打印机。已验证 window.print 调用与打印媒体仅显示标签。
- 本机二维码来源默认 127.0.0.1:5173，手机需配置可达的 PUBLIC_WEB_URL、前端监听及必要 CORS。生产未配置来源返回明确 PNG 配置错误，业务查询仍可用。
- Ant Design 生产公共块约 1.08 MB（gzip 约 340 KB），Vite 有体积提示；不影响构建。后续可按实际性能需求进一步分包。
- 有效期仅作记录，过期不会自动减少物理库存；本轮无撤销 / 冲正、领用、归还报废、审批、邮件预警或报表。
- 大量历史数据使用 OFFSET 分页，当前课程规模适用，尚未进行大规模压测。
- 沿用短期 JWT；退出清除浏览器会话，未实现服务端撤销。无 GitHub Push 或云部署。

### Sprint 04 准备

库存批次已有剩余数量与行锁扩展位置；流水预留 OUT / RETURN / SCRAP、project_no / usage，单据 / 材料 / 用户关系可追溯。下一轮可新增领用单并实现事务性批次扣减、非负条件更新和幂等，但本轮未开始。完成后等待用户人工验收。

