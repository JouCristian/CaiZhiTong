# v0.7.0 · 公开演示数据隔离

## 数据边界

GUEST-only 是只读演示身份。正式业务的“只读”权限不授予该身份。演示与正式业务共用 PostgreSQL 私有业务 Schema，但采用不同表、不同外键、不同 SQL 服务与路由，ID 相同也不互相查询。

| 演示表 | 内容与关联 |
| --- | --- |
| demo_category | 5 个合成分类 |
| demo_material | 25 种电阻、电容、芯片、开发板、传感及连接材料；仅关联 demo_category |
| demo_stock_batch | 50 个合成批次；仅关联 demo_material |
| demo_stock_event | 100 条示例入库 / 消耗；仅关联 demo_stock_batch，无人员或正式单据 |

DemoService 所有查询仅访问 demo_*。正式材料、库存、流水、预警与报表服务均不查询 Demo 表，不受 Demo 初始化影响。Demo 库存为静态合成样本，不提供真实入库、领用或归还报废写入。

## 初始化与金额

V7 创建结构，启动时 DemoCatalogInitializer 在事务和 PostgreSQL advisory lock 下执行 UTF-8 `db/demo/seed.sql`。固定主键与 ON CONFLICT DO NOTHING 保证重启、并发启动和重复初始化不重复插入或重置记录。没有管理员、学生或真实采购资料，也不需要公开初始化端点。

15 种正常库存、5 种低库存、5 种零库存；每种材料两个批次。批次余额等于示例入库减示例消耗。NUMERIC 计算库存价值，按 CNY / USD 分组，API 以十进制字符串返回；前端使用 Decimal.js 格式化金额，不合计不同币种。

旧 `seed-public-demo.ps1` 已移除，其通过正式材料 / 入库 API 制造演示记录的方案不再使用。`scripts/verify-public-demo.ps1` 仅登录并读取 Demo API 校验目录，不写正式业务。

## 服务端授权

统一 SecurityFilterChain 的匹配顺序：公开认证 / 安全系统元数据 → GET me / demo 要求认证 → 其余 demo 方法拒绝 → 正式 `/api/**` 要求 ADMIN / LAB_TECH / STUDENT / TEACHER / PURCHASER → Controller 原业务权限。

因此旧列表、详情、批次兼容查询、二维码、PDF / Excel、附件及未来新增正式 API，默认都拒绝 GUEST-only。前端隐藏按钮仅辅助体验。混合角色按合法业务角色判断，不因包含 GUEST 阻断既有角色。

每次 JWT 请求仍查数据库角色、enabled、auth_version。管理员通过原用户 API 调整角色，同一 JWT 的下一请求即可访问或失去相应权限；停用及密码重置撤销旧会话。Demo 响应不包含真实用户、操作人、经费和单据。

## 前端边界

GUEST-only 默认 `/demo/dashboard`，仅显示演示工作台、演示材料、演示库存。所有演示页面标注“公开演示 · 只读”，仅请求 Demo API 与本人 / 安全系统元数据。正式路径仍展示权限拒绝。OAuth 深链无法把游客带入正式业务页面，原业务角色仍进入原工作台。

## 验证方法

- 隔离 PostgreSQL 同时创建 A 正式哨兵材料与 B Demo 目录；检查游客可读 B，正式列表 / A 详情 / 批次 / 写入全部 403，A 保持不变，管理员仍能读取 A。
- 自动枚举所有实际映射的正式 GET Controller，逐一验证 GUEST-only 403，覆盖旧接口与下载接口。
- 验证 Demo 搜索、分页、筛选、精确金额、批次、示例变化、非法参数、未认证和幂等初始化；核验 Demo 外键没有正式表引用。
- 通过原用户管理 API 授予 STUDENT 再撤销为 GUEST，同一 JWT 立即改变权限。
- 在随机测试 Schema 先迁移至 V6，插入合成管理员、材料和真实结构的批次，再迁移 V7、重复初始化 Demo，核对原密码哈希、权限、材料和库存不被覆盖。
- 桌面和手机实际经过 Mock Provider 的 HTTP 授权码流程，记录演示页面请求，断言没有正式业务 API 调用。

实际结果见 [v0.7.0 开发记录](V0.7.0-GITHUB-OAUTH.md)。生产 V7 和真实 GitHub 登录需用户最终确认后验证；本设计文档不代表已上线。
