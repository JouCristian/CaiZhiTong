# Sprint 02 · 材料分类与档案

2026-11-03 开始，基于 Sprint 01 增量开发。保留认证、菜单权限及 API 包装，不实现库存批次、入库、领用或报表。

## 技术决策

- 基础分类最多两层（大类、子类）。品牌、型号只存于 material_item，浏览树通过真实数据库聚合生成第三、第四层，不建立重复的品牌 / 型号字典。
- 新增 Flyway V2，V1 不修改；材料编码由 PostgreSQL sequence 默认表达式生成，创建后不改变。
- 单价 NUMERIC(14,4) / BigDecimal，JSON 以字符串输出，前端 InputNumber stringMode 避免货币浮点计算。
- 安全库存为非负 NUMERIC(12,3)，是阈值，不是当前库存。材料档案不维护库存数量。
- 管理员维护分类和归档；管理员与实验员可新增、编辑、启停材料；其他角色只读。
- 归档记录保留原 ID 和关联，归档后只读，为后续批次、流水外键预留。
- 分类结构写操作使用事务级 advisory lock 串行校验，防止并发移动造成循环或超深。


## 实际完成

- 新增 V2__material_catalog.sql：material_category、material_item、material_code_seq，以及唯一约束、外键、检查约束和查询索引。未修改 V1。
- 分类真实创建、编辑、移动、排序、删除空类目；后台防止自引用、循环、超深和非空删除。
- 材料完整字段、自动编码、精确单价、安全阈值、详情、编辑、启停、归档与持久化。
- 服务端 keyword / categoryId / brand / model / status 筛选，创建时间稳定排序与分页（最多 100 条）。
- /facets 从数据库聚合属性，生成品牌 / 型号浏览节点；没有重复维护品牌、型号分类表。
- 材料页沿用 Sprint 01 侧栏、工具栏、字体与系统蓝，增加档案 / 类目页签、分类树、表格、详情和编辑抽屉。
- 管理员分类维护与归档、实验员档案新增 / 编辑 / 启停、其他角色只读；服务端方法授权同步执行。
- 工作台保留原 API 字段，仅将材料档案数量改为真实总数。其他三项指标仍为零，流转动态仍为空。
- 可选 demo-materials.sql 和 seed-demo.ps1；不自动导入日常库，不创建任何库存数量或批次。
- 独立浏览器数据库 caizhitong_e2e_test，测试脚本自动启动 8081 / 5180，并只清理自己启动的进程。日常 8080 / 5173 实例保留。

## 核心文件

| 位置 | 用途 |
| --- | --- |
| backend/src/main/resources/db/migration/V2__material_catalog.sql | 追加迁移 |
| backend/src/main/java/cn/caizhitong/controller/CategoryController.java | 分类 REST API |
| backend/src/main/java/cn/caizhitong/controller/MaterialController.java | 材料 REST API 与写入权限 |
| backend/src/main/java/cn/caizhitong/service/CategoryService.java | 树结构、循环、层级、同名与删除保护 |
| backend/src/main/java/cn/caizhitong/service/MaterialService.java | 材料写入、归档、只读历史及编码保护 |
| backend/src/main/resources/mapper/MaterialMapper.xml | 参数化搜索、关联类目、稳定排序、服务端分页 |
| frontend/src/materials/ | 材料 / 分类页面、表单、详情、样式与类型 |
| frontend/src/AccountMenu.tsx | 保留账户弹层，直接绑定退出按钮，修复生产退出菜单偶发问题 |
| backend/src/test/java/cn/caizhitong/MaterialIntegrationTest.java | 18 项新增集成用例 |
| frontend/e2e/materials.spec.ts | 5 个业务流程 × 桌面 / 手机，共 10 项 |
| scripts/test-browser.ps1 / smoke-materials.ps1 | 独立环境浏览器验收与真实 HTTP 写接口冒烟 |

## 接口与契约

认证、me、健康检查、统一包装、角色编码和导航路径保持兼容。新增接口详见 [API.md](API.md) 的 Sprint 02 章节。

材料编码由数据库生成，客户端自定义编码被拒绝，重复编码返回 409。归档后仅供查看，不提供恢复或硬删除。类别删除包括归档材料关联检查。

为了防止并发编辑覆盖归档状态，材料更新和状态修改在事务内先取行锁，再检查是否已归档。分类结构修改在事务内串行校验，重复名称还有数据库唯一索引兜底。

## 保留测试的必要适配

未删除 Sprint 01 的 23 项后端认证测试或 22 项浏览器认证 / 布局测试。

- 迁移数量断言从 1 改为 2，继续确认所有已执行迁移成功。
- 工作台断言改为对比真实 API 档案数，保留其余三项零值和空流转动态断言。
- 401 场景从材料开发占位页改为实际材料页面，再通过品牌导航触发工作台请求，继续验证真实失效处理。
- 普通 npm test 只运行认证回归，业务写入场景明确跳过；完整运行请使用 test-browser.ps1，它设置独立环境标记，执行全部业务写入测试。

## 开发中发现并修复

| 问题 | 处理 |
| --- | --- |
| MyBatis 对 Java record 的计算 getter 参数解析不同 | 使用 XML bind 绑定实际分页 / 关键词参数，查询和分页复验通过 |
| 同一测试事务中故意触发 PostgreSQL 唯一约束后事务中止 | 分类增加锁内同名预检查，数据库约束继续保留；约束测试独立覆盖 |
| 图标文字按钮的自动可访问名称带英文图标名 | 为业务操作提供明确中文 aria-label，装饰图标隐藏 |
| 搜索组件的语义角色为 searchbox | 自动化按实际组件语义定位，保留真实输入与查询行为 |
| 分类较多时 TreeSelect 虚拟列表只渲染当前可视项 | 验收通过可见搜索输入选择目标节点，不强制操作隐藏 DOM |
| 选择框内部 readonly 输入被已选值覆盖 | 自动化点击实际可操作的选择框外层，不使用强制点击 |
| 移动端分页器默认隐藏每页条数 | 补充移动样式，保留每页数量选择与自适应排列 |
| 多类目导致树面板过长 | 限制树列表高度并使用原生滚动；表格固定可视高度，保留服务端分页 |
| 演示 SQL 的 PL/pgSQL record 与表别名同名 | 改为独立别名，开发脚本连续执行两次并确认十份演示记录未重复 |
| 生产预览复现旧退出菜单偶发未触发 | 将账户弹层改为直接按钮事件，保持身份展示与退出行为；生产两场景各重复 5 轮全部通过，再完整回归 32 项通过 |

## 实际验收记录

已执行结果如下；未运行的检查不标记通过。

| 检查 | 结果 |
| --- | --- |
| scripts/test-backend.ps1 | 通过，41 项（原有 23 + 新增 18），0 失败 / 错误 / 跳过 |
| backend/mvnw.cmd -B -ntp package | 通过，重新执行 41 项测试并生成 backend-0.2.0.jar |
| npm.cmd run typecheck | 通过 |
| npm.cmd run build | 通过，存在 Ant Design 组件包体积提示 |
| scripts/test-browser.ps1 | 最终 32/32 全部通过（保留认证与布局 22 项 + 新增业务 10 项），桌面 / 手机各 16 项，无跳过 |
| 生产预览 4173 的浏览器冒烟（--repeat-each=5） | 修复后 10/10 通过：登录 / 恢复 / 退出与材料页 / 401 两个场景各执行 5 次 |
| scripts/smoke-api.ps1 | 通过，日常实例真实认证、身份、健康与旧模块权限 |
| scripts/smoke-materials.ps1（由隔离浏览器脚本运行） | 通过，22 次真实 HTTP 检查，覆盖写入、持久化、冲突、非法层级、越权、归档和删除保护 |
| seed-demo.ps1 -E2E 连续两次 | 通过，真实 PostgreSQL 中仍只有十份开发演示材料；日常库未自动导入 |
| Docker | 未执行，本轮不依赖 Docker |
| 真机 Safari / Firefox | 未执行，手机使用 Chrome 设备模拟 |
| GitHub Push / 云部署 | 未执行，按任务范围保留本地 |
| Sprint 02 人工验收 | 等待用户，详见 ACCEPTANCE.md |

最后一次完整浏览器运行 32 项全部通过，耗时约 1.9 分钟。最初业务自动化因组件定位不当失败；随后完整运行发现移动端每页条数被默认隐藏（31/32），修复后重新完整执行并通过。生产预览初轮 1/2，复现了旧账户菜单的退出偶发问题；改为直接按钮事件后，两场景重复 5 轮共 10 项全部通过，随后完整 32 项回归再次全部通过。早期失败与修复已列明，不将失败运行报告为通过。

## 截图

截图由真实 Chrome 在独立验收数据库中操作生成，包含测试材料，所有字段都来自已落库数据：

- [桌面材料列表](screenshots/sprint02-materials-desktop.png)
- [桌面分类维护](screenshots/sprint02-categories-desktop.png)
- [桌面材料详情](screenshots/sprint02-detail-desktop.png)
- [移动材料列表](screenshots/sprint02-materials-mobile.png)
- [移动分类维护](screenshots/sprint02-categories-mobile.png)
- [移动材料详情](screenshots/sprint02-detail-mobile.png)

## 已知边界

- 基础分类最多两层，展示树最多四层；品牌 / 型号以属性聚合显示，不可单独在分类页改名。
- 归档为本轮只读终态，保留历史关联，不支持恢复或硬删除。
- 当前库存统一为「未入库」，库存批次、入库、扫码、领用与报表仍未实现。
- 新增表格、树选择等组件后，Ant Design vendor 约 959 kB，gzip 约 303 kB；构建成功，但仍有大包提示。
- 浏览器写入验收记录留在独立测试库；日常库不自动添加这些记录或演示材料。
- 沿用 Sprint 01 的无状态 JWT：退出清除当前浏览器会话，已复制令牌仍持续至到期。

## Sprint 03 准备

不提前开发，仅确定扩展位置：

1. 用新的 V3 迁移建立 stock_batch 与 stock_transaction，外键引用 material_item.id。
2. 入库事务产生真实批次、数量和流水，材料档案继续不提供手工改库存字段。
3. 为归档材料的历史批次保留引用，并定义停用 / 归档时的新业务操作限制。
4. 复用权限、服务端分页、抽屉和统一错误反馈，新增库存与入库测试。
5. 用户完成本轮人工验收后再确定 Sprint 03 的具体范围。

