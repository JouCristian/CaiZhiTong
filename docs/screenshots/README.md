# 实际运行截图

这些 PNG 由 Playwright 在真实登录后台后生成，使用本地 PostgreSQL 与 Spring Boot 认证，无假登录或虚构业务记录。

- login-desktop.png：桌面登录页，1440px 视口。
- dashboard-desktop.png：管理员工作台，显示当前隔离测试库的真实指标与入库动态。
- login-mobile.png / dashboard-mobile.png：390px 视口的 Chromium 移动模拟，高像素密度截图。

运行前端测试会更新截图。基础登录 / 工作台截图随当前版本更新。

Sprint 02 文件名前缀为 sprint02-，涵盖 materials（材料列表）、categories（类目维护）、detail（完整材料详情）以及 desktop / mobile 两种视口。这些截图来自独立 caizhitong_e2e_test 数据库，展示的是通过真实 UI / API 写入的验收材料。它们不表示库存、入库或领用已实现。

Sprint 03 文件以 sprint03- 开头，覆盖库存、入库表单 / 详情、流水、二维码标签；Sprint 04 以 sprint04- 开头，覆盖领用表单 / 详情、本人历史、全量管理、月限额与后端生成的 PDF 渲染。均为真实隔离库运行截图，不是 AI 生成画面。移动端为 Chromium 模拟。

Sprint 05 覆盖归还报废、预警与报表；Sprint 06 覆盖用户权限、系统设置、管理员 / 学生工作台。`sprint06-dashboard-empty-desktop.png` 单独来自日常零业务库，只登录与读取，不插入夹具。其他业务图连接隔离测试数据库，不含真实学校用户或财务记录。

README 选用八个桌面界面，缩小显示尺寸并使用折叠展示，原图保留在本目录。各图可能由最终回归重新生成，因此记录开发阶段的文件名不代表当前源码尚缺少后续功能。
