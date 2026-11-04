# 数据库

PostgreSQL 15。默认数据库为 `caizhitong`，自动测试单独使用 `caizhitong_test`，禁止将 TEST_DB_URL 指向业务库。

## 结构与迁移

唯一迁移源位于 `backend/src/main/resources/db/migration/`。V1 建立用户、角色、关联表及六类角色，包含用户名唯一约束、角色唯一约束、复合主键、外键和角色关联索引。时间戳使用 TIMESTAMPTZ。后续更新用户时应同步 updated_at。

不在 SQL 中写入密码。开发演示账户仅在 dev profile 且 DEMO_ENABLED=true 时初始化；独立的生产首管理员初始化默认关闭，详见下方云适配说明。两者均使用 BCrypt，不重置已有账户密码。

## 本地数据库

首选根目录 `docker compose up -d`。无 Docker 的 Windows 环境可使用 `scripts/portable-postgres.ps1 start`，在被 Git 忽略的 .tools 与 .runtime 中下载 PostgreSQL 15.15、初始化并启动同版本真实 PostgreSQL。此路径只监听 127.0.0.1，启用 SCRAM 密码认证，下载来自 Maven Central 的 Zonky 预编译二进制。

两种方式共用配置和端口，选择一种运行，不能同时占用 5432。portable 脚本需要 JDK 17 和 Windows 自带 tar。

创建测试库：portable 使用 `scripts/portable-postgres.ps1 test-db`；Docker 使用 README 中的 createdb 命令。所有迁移由 Spring Boot 自动应用，不手工建表。

停止 portable：`scripts/portable-postgres.ps1 stop`。停止 Docker：`docker compose stop`。这些命令保留数据。请勿在没有备份时删除 volume 或 .runtime/pgdata。

## Sprint 02 · V2

新增 material_category、material_item、material_code_seq，不修改已经执行的 V1。

| 实体 | 关键字段与约束 |
| --- | --- |
| material_category | 自引用 parent_id 外键，name、description、sort_order，两个 TIMESTAMPTZ；同父级名称不区分大小写唯一；父子层级由事务锁和服务校验限制为两层 |
| material_item | category_id 外键，唯一 material_code，name、brand、model、spec、unit、unit_price、currency、safe_stock、status、description、时间戳 |
| material_code_seq | 单调序列，生成 MAT- 加十位数字；不依赖时间戳或前端随机数，失败事务消耗的序号不回收 |

unit_price 使用 NUMERIC(14,4) / BigDecimal；safe_stock 使用非负 NUMERIC(12,3)，是阈值，不是实际库存。状态为 ACTIVE、DISABLED、ARCHIVED。没有库存数量字段，也没有提前建立批次或流水。

材料归档保留主键与类目关联，后续 stock_batch / stock_transaction 可引用 material_item.id。关联任何材料（含归档）或子节点的类目不能删除。

索引覆盖父级排序、材料类目、品牌型号、创建时间与状态排序。列表使用参数化 SQL、COUNT、LIMIT / OFFSET，由服务端完成筛选与分页；不依赖前端过滤。

可选演示数据在 demo-materials.sql，通过根目录 scripts/seed-demo.ps1 执行；脚本默认不自动运行。仅开发环境可调用，反复执行不会重复插入匹配档案，不重置已有材料状态和价格。

浏览器写入测试使用独立 caizhitong_e2e_test，通过 scripts/test-browser.ps1 自动建立与启动测试服务；业务数据库 caizhitong 不承载自动化写入验收记录。

## Sprint 03 · V3

追加 V3__stock_inbound_ledger.sql，不改 V1 / V2。首次后端启动自动迁移，不自动向日常库写入入库记录。

| 表 | 关键字段与约束 |
| --- | --- |
| inbound_order | 唯一 order_no、UUID idempotency_key、SHA256 request_hash、created_by、received_at、COMPLETED 状态 |
| inbound_order_line | order_id / line_no 唯一、item_id、数量及名称 / 编码 / 规格 / 单位 / 币种 / 单价快照 |
| stock_batch | 唯一 batch_code、唯一 inbound_line_id、item_id 与明细复合外键、initial_quantity、quantity、价格快照、库位 / 有效期 |
| stock_transaction | 唯一 transaction_no、batch_id、类型、qty、before / after_quantity、操作人、关联入库单；预留 project_no / usage |

数量 NUMERIC(18,3)，价格 NUMERIC(14,4)，Java BigDecimal。数据库 CHECK 确保非负余额、正入库量及流水算术。序号失败不回收，出现间隔正常。金额按余额 × 价格精确求和并分币种返回。

一个事务写入四张表。UUID 唯一约束、请求摘要与 PostgreSQL advisory transaction lock 防止重复提交，材料按 ID 排序加行锁。已有批次后禁止通过档案修改计量单位，避免混合计量。

触发器拒绝入库单、明细及流水 UPDATE / DELETE，保护批次原始归属、入库数量和价格。未开放任意余额写入接口。后续领用 / 归还 / 报废需锁定或条件更新批次并追加流水，不能更改历史。过期仅提示，不自动扣实物库存。

索引覆盖材料批次、创建时间、单据及批次流水。JUnit 在独立 `_test` 库清理四张库存表与自有档案；HTTP / 浏览器记录保留于 caizhitong_e2e_test，日常 caizhitong 不承载自动写入用例。

## Sprint 04 · V4

只追加 V4__issue_orders_and_monthly_quota.sql，不修改已执行 V1–V3。

| 结构 | 关键字段与约束 |
| --- | --- |
| material_item.monthly_issue_limit | NUMERIC(18,3)，NULL 不限，正数为个人月限额 |
| issue_order | 唯一 ISS- 单号 / UUID 请求键、摘要、user_id、用户名 / 姓名快照、项目 / 用途、币种、精确总金额、自然月、业务时区、时间 |
| issue_order_line | 单据 / 行号唯一、单据 / 批次唯一、材料 / 批次复合外键、材料描述 / 单位 / 价格快照、数量和金额 |
| issue_monthly_usage | user_id / material_id / month_start 复合主键，used_quantity 非负 NUMERIC(28,3) |
| stock_transaction 扩展 | issue_order_id / issue_line_id，OUT 必须关联具体领用单和明细；单号、批次与明细的复合外键确保追溯一致 |

领用数量 NUMERIC(18,3)、单价 NUMERIC(14,4)、金额 NUMERIC(38,7)，金额 CHECK 等于数量乘快照价。自然月必须为月首日；领用单与明细继承不可变历史触发器。原 IN 流水与编号规则保留。

事务顺序：请求 advisory transaction lock（与入库不同命名空间）→ 相同内容幂等判断 → 排序材料行锁 → 排序批次行锁 → 插入或锁定个人月用量行 → 带限额条件 UPDATE → 单据 / 明细 → quantity>=delta 条件扣减 → OUT。任何一步失败回滚；没有浏览器内存保证库存的依赖。

材料锁与原入库 / 归档 / 单位保护兼容，也串行化同材料的限额设置与领用。该策略优先保证课程项目正确性，同一材料的高吞吐量场景尚未压测。跨批次额度在同一事务按材料汇总消费。

业务时区由 BUSINESS_TIME_ZONE 决定，并保存在单据。切换月份创建新额度行，不清空历史；系统运行后不应随意变更业务时区。未启用审批或预占库存。

JUnit 清理在既有测试库保护下显式包含七张业务表（领用单 / 明细 / 月用量与原四表），不使用面向业务库的清理接口。浏览器写入继续只在 caizhitong_e2e_test。


## Sprint 05 · V5

追加 `V5__returns_scrap_alerts_reports.sql`，不修改 V1–V4：

| 表 / 字段 | 作用 |
| --- | --- |
| return_order / return_order_line | 原领用关联、原批次、操作人、UUID / 哈希及正数数量 |
| scrap_order | 仓库批次报废、原因、可选照片、操作人和持久幂等 |
| stock_attachment | 服务端存储名、实际媒体类型、大小、SHA256、上传人 |
| inventory_alert | 触发 / 当前库存与阈值、周期状态、级别、处理备注和时间 |
| mail_notification | 每预警周期一条通知，发送状态、尝试次数、失败原因和时间 |
| stock_transaction 新外键 | RETURN / SCRAP 的单据与明细引用，仍是同一本流水 |
| issue_order.course | 可选课程，历史记录兼容 |
| sys_user.email | 采购员通知收件邮箱，Sprint 06 起由管理员在用户页面维护 |

归还明细使用复合外键保证所属原单 / 批次一致；普通 API 不允许修改或删除归还、报废及流水。库存变化在材料 → 批次 → 明细排序锁内，与业务 / 预警 / 通知登记同事务；行锁期间计算已归还总量，不新增可任意编辑的累计余额。活动预警使用部分唯一索引避免同轮重复。金额 NUMERIC / BigDecimal，数量 3 位小数；历史报表取原领用明细单价。

自动测试仅使用 `caizhitong_test` / `caizhitong_e2e_test`。日常库仅在启动时自动追加 V5 并扫描已有低库存，不插入测试库存操作。备份除 PostgreSQL 之外还需要 `STORAGE_ROOT` 照片目录。

## Sprint 06 · V6

仅追加 `V6__administration_and_settings.sql`，V1–V5 不变：

| 结构 | 字段与约束 |
| --- | --- |
| sys_user.last_login_at | 可空 TIMESTAMPTZ；仅成功登录更新，升级前记录显示暂无记录 |
| sys_user.auth_version | 非负 BIGINT，默认 0；停用 / 重置密码递增，撤销旧 JWT |
| idx_user_enabled_created | 账号状态、创建时间和 ID 的列表索引 |
| app_settings | `id=1` 唯一配置行；名称、描述、IANA 时区、学年、春秋四个日期、额外通知邮箱 |
| app_settings 并发与追踪 | revision、updated_by 外键、updated_at；旧 revision 返回 409 |

日期整组为空或整组有效，数据库检查秋季起止、春季起止及先后顺序，服务层进一步限制在学年 9 月至次年 8 月。时区为空时继承环境 BUSINESS_TIME_ZONE，默认 Asia/Shanghai；有库存业务后禁止修改。库存写入先锁配置行，再按原顺序锁材料 / 批次，避免与配置调整并发漏检。学期变更只影响默认查询，不改写历史流水，自定义日期优先。

配置不存 SMTP 密码、数据库密码或 JWT 密钥。邮件收件人合并环境值、配置值与启用采购员邮箱。安全公开元数据仅含名称、描述、时区与版本；完整设置与用户接口仅管理员访问。

用户管理串行 advisory transaction lock 内复核有效管理员，保护最后有效管理员。密码 BCrypt，查询投影不包含哈希 / 会话版本。角色每请求读取，停用 / 密码重置还校验 JWT 会话版本，重新启用后旧 JWT 不复活。

本轮日常库已迁移至 V6，材料 / 批次 / 领用数量迁移前后均为 0，保留原三个演示账户。JUnit 与浏览器写入仍仅在独立测试库；浏览器重置只针对专用 E2E 库，保留账号、迁移，清理自有业务夹具及全局配置。Java 源脚本中的中文常量使用 Unicode 转义，避免 Windows 默认源编码差异。

## Cloud Deployment 01 · 本地适配验证

本轮保持 V1–V6 文件与校验内容不变，无 V7。新增数据库配置通过独立 Flyway DataSource 执行原迁移，业务 Hikari 池与迁移连接分开；缺省继承已解析的 spring.datasource 属性，测试动态 URL 不会误指向日常库。

本地仍默认 public Schema。cloud profile 默认 caizhitong 私有 Schema，Flyway 只允许一个受限业务 Schema，必须与运行连接一致；不触碰 auth / storage / public 等平台 Schema。迁移前拒绝未启用 SSL、URL 内凭据、6543 事务池、校验关闭或 clean 启用。新 Schema 的 Flyway SCHEMA 标记不等于新增版本。

新增真实 PostgreSQL 集成测试只在 *_test 数据库的 caizhitong_cloud_test Schema 初始化、清理和创建测试管理员。清理前检查库名及 Schema；日常和线上数据库不承载这些写入。测试验证六版本、独立连接、PRIVATE Schema 对 PUBLIC 无 USAGE，以及业务与附件元数据重新查询。真实 Supabase 的平台角色和 Exposed schemas 仍需后续验收。

一次性生产管理员功能默认关闭，仅在明确启用且无有效 ADMIN 时执行，BCrypt、事务锁、同名账户拒绝接管，不重置既有密码；本轮仅在测试库运行。附件表继续保存原 storage_key，不改变表结构；Supabase 实现保存私有对象，本地实现保留。配置及未执行项见 [部署说明](../docs/DEPLOYMENT.md)与[云适配记录](../docs/CLOUD-DEPLOYMENT-01.md)。
## v0.7.0 追加迁移

V7 新增 oauth_identity、oauth_login_exchange 与 sys_user.password_login_enabled。原 V1–V6 保持不变；身份以 provider + 数字 ID 唯一关联，交换码只保存 SHA-256，60 秒过期并一次消费。原密码账户默认继续可用，不修改管理员密码或角色，不删除库存数据。生产执行以已审阅 PR 和 OAuth 配置确认为前提，细节见[开发记录](../docs/V0.7.0-GITHUB-OAUTH.md)。
