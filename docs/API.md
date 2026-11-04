# 接口与权限

最新增量为 v0.7.0，下方历史章节保留；冲突时以最新章节为准。

## v0.7.0 · GitHub OAuth 与只读体验

| 方法 / 路径 | 行为 |
| --- | --- |
| GET `/api/auth/oauth/config` | 公开、no-store，仅 githubEnabled；不返回 Client Secret |
| GET `/oauth2/authorization/github?challenge=…` | 顶层页面导航，challenge 为浏览器 verifier 的 S256；Spring 生成 state / provider PKCE，临时 Session |
| GET `/login/oauth2/code/github` | GitHub 注册的后端回调；必须匹配 Session / state，成功后跳转固定前端 `/oauth/callback#code=…`，没有 JWT |
| POST `/api/auth/oauth/exchange` | 公开 JSON `{code,verifier}`；60 秒、一次性、浏览器绑定，返回与密码登录相同 LoginResponse；no-store |

兑换参数只允许 Base64URL；code 为 43 字，verifier 43–128 字。错误格式 400，已消费 / 过期 / verifier 不符 / 会话撤销 401，功能关闭 404。前端先移除 URL fragment 再兑换；外部 JWT 不能从 URL 读取。回调失败仅传 cancelled / state_invalid / disabled / failed 等白名单错误码。

OAuth 身份仅使用 GitHub 数字 ID，永不按邮箱或 handle 自动关联。新建用户 password_login_enabled=false，角色仅 GUEST；原密码用户无变化，管理员明确重置后可以开启 OAuth 用户的密码入口。GET me / LoginResponse.user 新增可选 githubLogin / avatarUrl，旧字段兼容。

GUEST-only 账户仅允许读取本人 `/api/auth/me` 和独立 `/api/demo/**`，所有正式 `/api/**` 在统一 SecurityFilterChain 入口返回 403，包括材料、库存、工作台、兼容接口、二维码、下载、报表、照片及所有写入；无需等到 Controller 参数解析。公开 login / OAuth config / exchange / system 元数据沿用原规则。混合角色只要持有有效正式角色，仍按原业务授权判断；每个请求从数据库取最新角色，管理员升级 STUDENT 或撤回到 GUEST 后同一 JWT 的下一请求即生效。

### 独立只读 Demo API

下列 GET 均要求有效 JWT；未认证 401。GUEST 导航仅 demo/dashboard、demo/materials、demo/stocks。正式角色也可认证读取 Demo，但原工作台与导航不变。`/api/demo/**` 所有非 GET 请求统一 403，无任何写入 Controller。

| 路径 | 返回 / 查询 |
| --- | --- |
| `/api/demo/dashboard` | scope=SYNTHETIC_DEMO、readOnly=true、材料 / 有库存 / 低库存 / 零库存数量、批次数、按币种分开的库存价值、分类分布 |
| `/api/demo/categories` | 独立 Demo 分类 id / name |
| `/api/demo/materials` | records / total / page / size；page≥1，size 1–100，keyword≤100（名称 / 编码 / 型号），categoryId，stockStatus=NORMAL / LOW / ZERO；服务端筛选分页，按 ID 稳定排序 |
| `/api/demo/stocks` | 同一独立合成目录的库存汇总分页，查询参数同上 |
| `/api/demo/materials/{id}` | 完整合成档案与该 Demo 材料批次 |
| `/api/demo/batches/{id}` | 合成批次与示例 IN / OUT 变化，不含关联人、正式单据或附件 |

金额 / 数量为精确十进制字符串。非法查询 400，Demo ID 不存在 404。查询只连接 demo_* 表，ID 即使与正式表相同也不会回退查询正式记录。初始化及隔离依据见 [设计说明](DEMO-ISOLATION.md)。

真实 GitHub App 的生产配置、临时会话与云平台验证边界见 [v0.7.0 记录](V0.7.0-GITHUB-OAUTH.md)。

## Sprint 06 · 用户、设置与仪表盘

本节覆盖旧版占位状态，原认证与业务 API 契约保持兼容。

| 方法 / 路径 | 权限与用途 |
| --- | --- |
| GET `/api/users` | ADMIN，keyword（用户名 / 姓名 / 邮箱）、enabled、page、size 服务端搜索分页 |
| GET `/api/users/{id}` | ADMIN，用户详情，明确排除密码、哈希及令牌版本 |
| POST `/api/users` | ADMIN，新建用户及现有角色 |
| PUT `/api/users/{id}` | ADMIN，姓名、邮箱、roles、enabled 原子更新；用户名不变 |
| PATCH `/api/users/{id}/status` | ADMIN，`{"enabled":false}`；禁止停用自身或最后有效管理员 |
| PATCH `/api/users/{id}/password` | ADMIN，`{"password":"REPLACE_WITH_LOCAL_PASSWORD"}`，BCrypt 存储并撤销旧会话 |
| GET `/api/settings` | ADMIN，配置、revision、业务时区锁定状态、邮件准备状态和最近通知 |
| PUT `/api/settings` | ADMIN，完整保存全局业务设置，revision 冲突 409 |
| GET `/api/public/system` | 公开安全元数据，仅 systemName / systemDescription / businessZone / version，no-store |
| GET `/api/workspace/dashboard` | 正式业务角色，服务端决定 PERSONAL / AGGREGATE / ALL 统计范围；GUEST-only 403 |

创建请求示例（示例密码必须由本地开发者替换，不记录或返回）：

```json
{"username":"new_student","credentials":{"password":"REPLACE_WITH_LOCAL_PASSWORD"},"profile":{"displayName":"实验同学","email":"student@example.test","roles":["STUDENT"],"enabled":true}}
```

用户名须为 3–64 位字母、数字、点、横线或下划线，使用现有数据库唯一约束（区分大小写）；姓名最多 64 字，邮箱可空且最长 254 字，角色为原六类编码且至少一个。密码 12–72 字符、UTF-8 最多 72 字节，DTO 的字符串表示也会脱敏。列表 / 详情返回 id、username、displayName、email（可空）、roles、enabled、createdAt、updatedAt、lastLoginAt（无 V6 后登录记录时为空），不返回任何凭据。

停用 / 密码重置递增 auth_version；新 JWT 包含 av，旧版无 av 按 0 兼容。每次请求仍读取数据库启用状态、角色及会话版本。重新启用不复活停用前令牌；角色调整后旧令牌只能使用当前角色权限。维护使用串行管理锁，锁内复核管理员身份，禁止自身停用 / 自降角色，且并发操作不能删除最后有效管理员。成功登录写入 last_login_at，普通 API 访问不更改最近登录时间。

配置 PUT 字段：revision、systemName、systemDescription、businessZone、academicYear、autumnStart、autumnEnd、springStart、springEnd、notificationRecipients（邮箱数组）。系统名最多 40 字、描述 200 字；学年 1990–2200；日期必须处于该学年 9 月至次年 8 月、秋季先于春季且不重叠。时区为合法 IANA 标识，首次库存业务前可修改，已有入库 / 领用后返回 409 锁定提示；与库存写入协调共享 / 独占配置行锁，避免并发漏检。version 只读。

配置详情包含 smtpConfigured、notificationDispatchEnabled、notificationStatus（READY / DISABLED / NOT_CONFIGURED）、notificationReason、effectiveRecipients、recentNotifications、zoneLocked、timeZones。SMTP 凭据、JWT 密钥不入配置表、不通过接口返回。额外收件人与环境收件人及启用采购员邮箱合并。SMTP 准备状态不代表外部投递成功，实际结果以通知记录为准。

报表在请求学年匹配已配置学年时采用保存的学期日期；未配置或其他学年沿用标准边界。自定义 from / to 始终优先，返回 meta.semesterConfigured 标明配置是否适用。

工作台保留旧 `/api/workspace/overview` 契约。新 dashboard 返回 scope、from / to、businessZone、stockValues（按币种）、lowStockCount、monthTotals（按币种与单位）、topMaterials（每组 TOP10）、trend（按实际日）、funding（label / currency / amount / percentage）、ranking、canViewPeople、notes。数量 / 金额为十进制字符串。净消耗沿用 Sprint 05 逻辑，资金占比明确使用领用金额（未扣归还），fundingDimension 标明 PROJECT / CATEGORY，仓库报废单列。

ADMIN / LAB_TECH 可看全量及个人排行；TEACHER / PURCHASER 可看授权汇总，但资金占比仅按材料分类，不返回受限项目明细或排行身份；STUDENT 的业务统计强制本人，不接受任意 userId；v0.7.0 起 GUEST-only 完全拒绝正式工作台统计，改用独立 Demo 工作台。普通角色不能通过隐藏按钮或请求参数扩展统计范围。

## Sprint 05 · 归还、报废、预警与报表

金额与数量 JSON 为精确十进制字符串；ID / 分页计数为数字。以下接口都要求 Bearer JWT。统一失败格式不变，校验 400、无权限 403、不存在 404、库存 / 累计归还 / 幂等冲突 409。日期使用业务时区，默认 `Asia/Shanghai`，日期区间包含结束日。

| 方法 / 路径 | 权限与行为 |
| --- | --- |
| POST `/api/returns` | ADMIN / LAB_TECH，登记完好归还 |
| GET `/api/returns` | ADMIN / LAB_TECH 全部，STUDENT / TEACHER 仅本人 |
| GET `/api/returns/{id}` | 同上，返回 `order` 和 `lines` |
| POST `/api/scraps` | ADMIN / LAB_TECH，扣仓库余额 |
| GET `/api/scraps`、GET `/api/scraps/{id}` | 库存模块角色：ADMIN / LAB_TECH / TEACHER / PURCHASER |
| POST `/api/scraps/attachments` | ADMIN / LAB_TECH，multipart 字段 `file`，返回 id / mediaType / byteSize / createdAt |
| GET `/api/scraps/attachments/{id}` | 认证图片字节、no-store、nosniff。未绑定仅管理人员；绑定后库存模块角色可读；学生 / 游客拒绝 |
| GET `/api/alerts`、`/api/alerts/summary`、`/api/alerts/{id}` | 库存模块角色，summary 为 lowMaterialCount / pendingCount / acknowledgedCount |
| POST `/api/alerts/{id}/acknowledge` | ADMIN / LAB_TECH / PURCHASER，`{"note":"处理备注"}`；已恢复事件 409 |
| POST `/api/alerts/rescan` | ADMIN / LAB_TECH，扫描已有材料，返回 scanned |
| POST `/api/alerts/{id}/mail-retry` | ADMIN / LAB_TECH，仅 FAILED / NOT_CONFIGURED 可重试，返回 PENDING |
| GET `/api/reports/semester` | ADMIN / LAB_TECH / TEACHER / PURCHASER |
| GET `/api/reports/funding` | ADMIN / LAB_TECH |
| GET `/api/reports/personal` | 报表模块角色；非管理员 / 实验员只能本人，STUDENT 已加入报表模块 |
| GET `/api/reports/{semester\|funding\|personal}/export/{xlsx\|pdf}` | 与预览相同权限，附件流、UTF-8 中文文件名、no-store |
| GET `/api/reports/users?keyword=` | ADMIN / LAB_TECH，最多 50 个匹配账户，用于用户筛选 |

归还示例：

```json
{"idempotencyKey":"替换为合法 UUID","issueOrderId":1,"lines":[{"issueOrderLineId":1,"quantity":"5"}],"remark":"完好退回"}
```

归还不接受目标批次参数，始终由原领用明细决定；累计归还在材料 / 批次 / 明细排序锁内检查。一次 1–20 行，同一明细不得重复；数量 15 位整数、3 位小数，最少 0.001。不返还月度领用额度。历史单据不可更改或删除。

报废示例：

```json
{"idempotencyKey":"替换为合法 UUID","batchId":1,"quantity":"3","reason":"仓库受潮","remark":"核对后报废","attachmentId":1}
```

reason 必填，最多 1000 字；remark 最多 2000 字；attachmentId 可省略，必须先成功上传且未被其他报废单绑定。照片类型通过 ImageIO 解码确认，支持 JPG / PNG / WebP；最大 5 MB、2000 万像素，服务器生成存储名。路径不从客户端获取，拒绝遍历及符号链接文件。

POST 使用持久 UUID 幂等；同 UUID 同操作人同内容重试返回原结果，变更内容或操作人 409。前端结果未知时保留 sessionStorage 请求，只重试原请求。库存变化、业务记录、流水、预警与通知登记在同一事务；SMTP 在提交后运行。

归还 / 报废列表参数：`keyword,user,from,to,page,size`（user 仅归还）；keyword 支持业务单号、原单号 / 材料；page 默认 1、size 默认 20、上限 100。返回 `{records,total,page,size}`。预警列表参数 `keyword,status,page,size`，status 为 ACTIVE / ACKNOWLEDGED / RESOLVED。预警详情包含触发与最近检测数量 / 阈值（已恢复事件保留恢复时快照）、处理人 / 备注 / 时间及 mailStatus / mailAttempts / failureReason / sentAt。通知状态为 PENDING、NOT_CONFIGURED、SENDING、SENT、FAILED；发送结果不确定的 SENDING 不自动重试，需先核查实际投递。

报表查询：`academicYear,term,from,to,categoryId,keyword,course,projectNo,userId,page,size`。学年为起始年份，term 为 AUTUMN / SPRING；秋季为当年 9/1 至次年 1/31，春季次年 2/1 至 8/31；自定义 from / to 必须成对，优先于学期。keyword 材料名称 / 编码模糊匹配，course 模糊匹配，projectNo 精确匹配。userId 非管理人员只允许个人表自己的 ID。预览返回：

```text
meta: title,type,academicYear,term,from,to,businessZone,generatedAt,fileStem,notes[,userId]
records: materialCode,materialName,spec,unit,currency,categoryPath,
         issueQuantity,returnQuantity,netQuantity,issueAmount,returnAmount,netAmount,
         scrapQuantity,scrapAmount,lastEventAt
totals: 按 currency + unit 分组的上述数量和金额
total,page,size
```

经费 / 个人表还包含 lineId、issueNo、recipientName、accountName、projectNo、course、usage、batchCode、issuedAt、unitPriceSnapshot、originalQuantity、returnedTotal。每行保留原领用明细，不能把不同历史单价任意合并。returnedTotal 为截至查询时的累计归还，本期 returnQuantity 按实际归还日期统计。净消耗允许负数，报废独立计量；分类按当前材料归类，材料字段 / 单价取历史快照。没有课程显示“未关联课程”。

导出使用同一统计逻辑、不采用前端当前页数据；最多 10,000 行，超限返回 400。XLSX 有标题、日期、冻结表头、单位币种分组汇总和统计口径页；超过 15 位有效数字的值用文本保留精度。PDF 使用内嵌中文字体、A3 横向、多页重复表头与长行续页。空数据返回合法文件。模板为通用模板，未验证任何真实财务系统。

原 POST `/api/issues` 仅新增选填 `course`（100 字），旧请求及课程为空时的幂等摘要保持兼容；明细增加 `returnedQuantity`、`returnableQuantity`。库存流水新增 RETURN / SCRAP 关联单号与 orderId。非管理读者只见公开 IN / SCRAP、本人 OUT 与本人原领用对应的 RETURN，不能绕过单据权限读取他人领用。

下方保留认证与导航契约；分类档案、库存入库和领用分别见 Sprint 02–04 章节。历史说明由后续 Sprint 的兼容更新覆盖。

API 前缀为 `/api`，JSON 编码为 UTF-8。业务成功的 HTTP 状态为 200，统一响应：

```json
{"code":0,"message":"成功","data":{}}
```

失败使用对应 HTTP 状态与非零 code，错误信息不泄露堆栈、SQL 或密码；无 data 时省略该字段。Actuator 使用标准健康响应 `{"status":"UP"}`，CORS 预检拒绝使用 Spring 标准 403 响应。

## 认证

### POST /api/auth/login

公开接口，请求示例（替换为自己的本地密码）：

```json
{"username":"admin","password":"YOUR_LOCAL_DEMO_PASSWORD"}
```

成功 data 包含 `accessToken`、`tokenType=Bearer`、ISO 时间 `expiresAt`、`user`。user 字段为 id、username、displayName、roles、modules，不含密码哈希。

错误：参数无效 / 非法 JSON 返回 400；账户不存在、密码错误或账户禁用统一返回 401「用户名或密码错误」。密码按 BCrypt 72 字节限制验证 UTF-8 长度。

### GET /api/auth/me

请求头：`Authorization: Bearer <accessToken>`。返回当前 UserView，数据实时从 PostgreSQL 读取。无令牌、签名错误、到期、issuer 错误、账户不存在或禁用均返回 401。

JWT 使用 HS256 签名；密钥来自环境变量，启动时检查 Base64 与最少 32 字节要求。强制校验 issuer、expiresAt、subject 与签名，时钟过期宽限为 0。前端倒计时只负责用户体验，实际授权由服务器完成。

### GET /actuator/health

公开，数据库断连会影响健康状态；不暴露详细组件信息。

## 工作空间基础接口

| 接口 | 作用 |
| --- | --- |
| GET /api/workspace/overview | 真实材料数 / 有库存材料数 / 低库存数、最近五条入库动态；pending=0，businessAvailable=true |
| GET /api/workspace/modules/{module} | 校验模块访问权限；materials / inventory / inbound 为 ready，其余 planned |

第二个接口仅返回开发状态元信息，不提供材料 / 库存业务。无权访问返回 403。不存在的普通 API 路径，已认证用户访问返回 404；未认证用户先返回 401。

## 角色访问矩阵

下表为正式业务导航；v0.7.0 GUEST-only 仅有独立演示工作台、演示材料和演示库存。

| 页面 | 游客 | 学生 | 实验员 | 教师 | 采购员 | 管理员 |
| --- | --- | --- | --- | --- | --- | --- |
| 工作台 |  | ✓ | ✓ | ✓ | ✓ | ✓ |
| 材料管理 |  | ✓ | ✓ | ✓ | ✓ | ✓ |
| 库存管理 |  |  | ✓ | ✓ | ✓ | ✓ |
| 入库管理 |  |  | ✓ |  | ✓ | ✓ |
| 领用管理 |  | ✓ | ✓ | ✓ |  | ✓ |
| 归还与报废 |  | ✓ | ✓ | ✓ |  | ✓ |
| 库存预警 |  |  | ✓ | ✓ | ✓ | ✓ |
| 数据报表 |  |  | ✓ | ✓ | ✓ | ✓ |
| 用户与权限 |  |  |  |  |  | ✓ |
| 系统设置 |  |  |  |  |  | ✓ |

多角色取权限并集。游客是已分配 GUEST 角色的账户，匿名访问仍需登录。只初始化管理员、实验员、学生三类本地演示账户；其余三类角色通过独立测试库的真实账户完成权限测试。

权威规则为后端 `ModuleAccess`。UserView.modules 返回可访问模块，前端据此展示菜单与拦截路由。每个模块请求仍经 `@PreAuthorize` 检查，不能通过手改前端状态绕过。

## 会话与退出

JWT 存于 sessionStorage，刷新时通过 /me 验证。临时网络错误保留令牌并提供重试，401 清除会话并返回登录，403 展示权限提示。用户退出清除当前浏览器令牌，不请求不存在的退出接口。

本轮没有刷新令牌、服务端撤销列表或共享会话存储。已复制的有效 JWT 在到期前仍可使用；账户禁用或角色修改立即影响后续请求。生产运行需 HTTPS、随机密钥与独立生产账户。

## Sprint 02 · 材料分类与档案

沿用上述认证和统一响应，所有接口要求 Bearer JWT。材料读取允许六类既有角色；新增、修改、启停只允许 ADMIN / LAB_TECH；分类写操作及归档只允许 ADMIN。JWT 角色在每次请求时由数据库读取。

### 基础类目

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | /api/material-categories/tree | 返回按 sortOrder、ID 排序的两层基础分类树 |
| POST | /api/material-categories | 创建类目，管理员 |
| PUT | /api/material-categories/{id} | 更新名称、父级、描述、排序，管理员 |
| DELETE | /api/material-categories/{id} | 删除空类目，管理员 |

请求字段：name（必填，最多 100 字符）、parentId（null 表示大类）、description（最多 2000 字符）、sortOrder（0–9999，默认 0）。

树节点包含 id、parentId、name、description、sortOrder、createdAt、updatedAt、materialCount、children。materialCount 是直接关联档案数量，包含归档记录，不是库存。

采用两层基础分类。品牌与型号只在材料档案维护，通过 facets 聚合生成浏览树第三、第四层，不重复建立分类记录。父级不能为自身、后代或已有父级的子类；含子类的大类不能移动为子类。同一父级下名称不区分大小写且唯一。删除仍有子节点或材料（包括归档材料）的类目返回 409。

### 材料接口

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | /api/materials | 服务端搜索、筛选、排序、分页 |
| GET | /api/materials/facets | 从数据库 GROUP BY categoryId、brand、model 返回属性浏览节点及数量 |
| GET | /api/materials/{id} | 完整档案详情 |
| POST | /api/materials | 创建材料，系统生成编码 |
| PUT | /api/materials/{id} | 更新可编辑档案，编码和创建时间保持不变 |
| PATCH | /api/materials/{id}/status | 启用、停用或管理员归档 |

创建 / 更新示例：

```json
{
  "categoryId": 1,
  "name": "金属膜电阻 10kΩ",
  "brand": "国巨",
  "model": "MFR-25",
  "spec": "10kΩ，±1%，0.25W",
  "unit": "个",
  "unitPrice": "0.1200",
  "currency": "CNY",
  "safeStock": "100",
  "status": "ACTIVE",
  "description": "用于实验电路"
}
```

- categoryId、name、unit、unitPrice、safeStock 必填。
- 名称 / 品牌 / 型号最多 100 字符，单位最多 20，规格与备注最多 2000。
- unitPrice 为非负精确十进制，最多 10 位整数与 4 位小数；数据库 NUMERIC(14,4)，Java BigDecimal，JSON 字符串。
- safeStock 为非负阈值，最多 9 位整数与 3 位小数；JSON 字符串，不代表当前库存。
- currency 默认 CNY，三位大写字母；状态默认 ACTIVE，表单允许 ACTIVE / DISABLED。
- materialCode 由后端数据库序列生成，例如 MAT-0000000001。客户端无须发送，创建时自定义编码返回 400，指定已存在编码返回 409；编辑时不得改变编码。
- 返回字段还包括 id、materialCode、categoryPath、createdAt、updatedAt；没有可编辑库存数量。
- 状态 PATCH 请求为 `{"status":"DISABLED"}`、ACTIVE 或 ARCHIVED。
- 归档只能由管理员执行，保留原 ID、编码和关联。归档后只读，修改或重新启用返回 409；重复归档幂等成功。本轮不提供硬删除或恢复归档接口。

列表参数：

| 参数 | 说明 |
| --- | --- |
| keyword | 材料名称或编码包含匹配，最多 100 字符；百分号、下划线按普通字符处理 |
| categoryId | 类目筛选；选择大类时包含其子类 |
| brand / model | 按完整属性值筛选 |
| status | ACTIVE / DISABLED / ARCHIVED；省略表示全部 |
| page / size | 默认 1 / 20，size 最大 100，page 从 1 开始 |
| sort / order | sort 仅支持 createdAt；order 为 asc / desc，默认 desc；同时间以 ID 保持稳定顺序 |

成功 data：

```json
{"records":[],"total":0,"page":1,"size":20}
```

错误：400 参数或层级错误，401 未登录 / 失效，403 无权写入，404 不存在的类目或档案，409 名称 / 编码冲突、受保护类目删除、归档档案修改。数据库约束冲突不会返回 SQL 详情。

### 工作台兼容更新

保留 /api/workspace/overview 的全部字段与类型。metrics.materials 改为数据库真实档案总数（含归档），其余库存 / 申请 / 预警仍为 0，activities 为空；businessAvailable=false 继续表明完整库存流转尚未上线。

/api/workspace/modules/materials 返回 status=ready，其余未开发模块仍为 planned。

## Sprint 03 · 库存、入库与标签

本节覆盖旧工作台状态：businessAvailable=true；metrics.materials 为真实档案数，stock 为有库存材料种数，alerts 为低于安全库存的材料种数，pending=0；activities 为最近五条真实 IN 的 id、title、detail、createdAt。

| 方法与路径 | 权限与 data |
| --- | --- |
| GET /api/stocks | 六角色材料读取权限；records / total / page / size |
| GET /api/stocks/summary | 六角色；materialCount、stockedMaterialCount、lowStockCount、values（分币种 amount 字符串） |
| GET /api/stocks/{materialId}/batches | 六角色；material 汇总、batches 分页 |
| GET /api/stock-batches/{batchCode} | 六角色；材料快照、数量、来源单据、经办人及 qrContent |
| GET /api/stock-batches/{batchCode}/qrcode | 六角色；image/png 二进制，no-store；失败为统一 JSON |
| GET /api/stock-transactions | inventory 模块：ADMIN / LAB_TECH / TEACHER / PURCHASER；分页 |
| GET /api/inbound-orders | inbound 模块：ADMIN / LAB_TECH / PURCHASER；分页 |
| GET /api/inbound-orders/{id} | 同上；order、lines、totals（分币种） |
| POST /api/inbound-orders | 仅 ADMIN / LAB_TECH；返回持久化后的完整详情 |

保留原菜单矩阵，学生从材料详情及二维码路由只读访问库存；v0.7.0 起游客对正式库存及二维码接口一律 403，采购员不能入库。全部要求 JWT，未登录 401，无权限 403。

### 查询参数

- page 默认 1、size 默认 20，最多 100。库存按材料 ID 倒序；其他列表按时间与 ID 倒序。
- 库存：keyword 名称 / 编码，categoryId（包含子类），stockStatus=ZERO|LOW|NORMAL，itemId；materialIds=1,2,3 用于当前材料页批量查询，最多 100 个。
- LOW 包括低于安全阈值的零库存材料；NORMAL 为数量大于零且不低于阈值。
- 入库历史：keyword 单号、from / to 创建日期。
- 流水：keyword 材料名 / 编码 / 交易号，itemId，精确 batchCode，txnType=IN|OUT|RETURN|SCRAP，from / to。
- 日期 YYYY-MM-DD，按 Asia/Shanghai 起始日零点至结束日次日零点（不含）。开始晚于结束返回 400。keyword 最多 100 字，百分号 / 下划线为字面值。

### 入库请求

```json
{
  "idempotencyKey": "71858130-39c5-423f-bcba-1fce77f79819",
  "remark": "实验室采购到货",
  "lines": [
    {"itemId": 1, "quantity": "2.125", "unitPrice": "0.1234", "currency": "CNY", "unit": "个", "location": "A-01", "expireDate": "2027-12-31", "remark": "验收合格"}
  ]
}
```

- UUID 必填，1–50 条明细。itemId / quantity 必填；数量大于零、最多 15 位整数与 3 位小数；价格非负、最多 10 位整数与 4 位小数。数量、价格、金额响应均为精确字符串。
- receivedAt 可选 ISO offset 时间，不晚于当前时刻，省略则服务器生成；有效期不能早于入库日期。库位最多 100 字，单 / 行备注各最多 2000 字。
- unitPrice 省略使用当前档案价格；currency / unit 可省略，传入必须与锁定档案一致，否则 409，避免过期选择。
- 每行创建独立批次，同材料可多行。单号 IN-、批次 BAT-、交易 TXN- 加十位数据库序号；回滚可能留下序号间隔。
- 同 UUID、经办人和规范化内容返回原单，不重复入库。不同内容 / 经办人返回 409；重试不替换历史快照。
- 不存在材料 404；停用 / 归档、单位 / 币种变化 409；非法数量 / 日期 400。任何写入失败整体回滚。
- 没有直接编辑 / 删除单据、批次余额、流水接口。已有批次的材料不能改计量单位（409），其他档案字段与价格仍可正常维护。

详情 lines 含 itemId、itemName、itemCode、spec、unit、initialQuantity、quantity、unitPriceSnapshot、currency、batchCode、location、expireDate、orderId、orderNo、operatorName、receivedAt、remark。历史金额按 initialQuantity × 快照价，库存价值按 quantity × 快照价，分币种汇总。

二维码仅编码 PUBLIC_WEB_URL/stock/batches/{batchCode}，不含 JWT。前端登录后恢复原路径，不存在批次 404。生产未配置 PUBLIC_WEB_URL 时 qrContent=null，入库 / 查询正常，二维码 PNG 返回 503 配置提示。

## Sprint 04 · 领用与个人月度限额

保留所有旧认证、材料、入库和二维码路径；新增下表。普通 JSON 仍使用统一 envelope，成功 HTTP 200。PDF 成功为二进制，失败仍为统一 JSON。

| 方法与路径 | 权限 / 行为 |
| --- | --- |
| POST /api/issues | ADMIN / LAB_TECH / STUDENT / TEACHER；本人领用，事务提交后返回 order / lines |
| GET /api/issues/my | 上述四类；强制当前身份，只查本人，不接受切换用户 |
| GET /api/issues | ADMIN / LAB_TECH；全量领用分页 |
| GET /api/issues/{id} | 本人，或 ADMIN / LAB_TECH；他人私有记录返回 403，不存在 404 |
| GET /api/issues/{id}/pdf | 同详情权限；application/pdf，attachment，Cache-Control=no-store |
| GET /api/materials/{id}/issue-quota | 可领用四类角色；只返回当前用户该材料、本自然月额度 |
| GET /api/materials/{id}/issue-batches | 可领用四类角色；仅有余额批次，page / size，返回分页；停用 / 归档 409 |
| PATCH /api/materials/{id}/issue-limit | 仅 ADMIN；独立额度设置，不改原材料 PUT 契约 |

采购员 / 游客不能提交或读取私有领用单。身份从 JWT 的实际数据库用户取得，不处理客户端 userId 代领。多角色按权限并集。

### POST /api/issues

```json
{
  "idempotencyKey": "71858130-39c5-423f-bcba-1fce77f79819",
  "projectNo": "LAB-2026-001",
  "usage": "模拟电路课程实验",
  "remark": "选填",
  "lines": [{"batchId": 1, "quantity": "20.125"}]
}
```

- UUID 必填。projectNo 非空、最多 100 字；usage 非空、最多 500 字；remark 最多 2000 字。
- 1–20 个不同批次，同批次重复行返回 400，应先合并。quantity 必填且大于零，最多 15 位整数、3 位小数。不能传入余额、领用人、单价或金额控制服务端结果。
- 当前材料必须 ACTIVE，批次必须存在，数量不能超过该批次余额。库存不足与个人月额不足分别返回明确 409 消息。
- 金额采用批次入库价格 / 单位 / 币种快照，BigDecimal 精确计算。一个单据允许多材料 / 多批次但只有一种币种，混币种返回 400，须分单。
- 同 UUID、相同真实用户和规范化内容重试返回原单，不重复扣库存、额度或生成流水；同键不同内容 / 用户返回 409。重试先识别既有结果，材料后续停用、改价、限额变更不改变成功结果。
- 单号 ISS- 加十位数据库序号，失败事务可能留下序号间隔。入库、领用使用独立幂等命名空间。
- 全部写入在同一事务：材料 / 批次检查与锁定、额度条件更新、单据与明细、余额条件扣减、OUT 流水；任一步失败整体回滚。

返回 order 包含 id、issueNo、userId、accountName、recipientName、projectNo、usage、remark、status=COMPLETED、currency、totalAmount、monthStart、businessZone、createdAt、lineCount、materialNames、batchCodes、quantitySummary。用户名和姓名是提交时快照，不返回幂等键或摘要。

lines 包含 id、materialId、batchId、materialCode、materialName、spec、unit、batchCode、quantity、unitPriceSnapshot、amount、transactionNo、beforeQuantity、afterQuantity。所有数量 / 价格 / 金额为精确字符串。历史不可通过普通业务接口修改 / 删除；本轮没有撤销、代领、审批。

### 历史查询

page 默认 1、size 默认 20、最大 100；时间与 ID 倒序。

- keyword：单号、材料名称 / 编码、项目号包含匹配，最多 100 字。
- material：材料名称 / 编码包含匹配。
- projectNo：精确项目号。
- user：领用人姓名 / 用户名包含匹配（全量管理使用）；不会改变 /my 的身份范围。
- from / to：YYYY-MM-DD，按 BUSINESS_TIME_ZONE 起始日零点至结束日次日零点（不含）查询；倒置范围返回 400。

### 额度

PATCH 示例 `{"monthlyIssueLimit":"50.000"}`；清除用 `{"monthlyIssueLimit":null}`。NULL 表示不限，正数为限额，0 / 负数 / 超精度返回 400。归档材料不能配置。旧材料 PUT 不读取此字段，实验员不能借普通编辑清空或变更额度。

GET quota 返回 materialId、monthStart、businessZone、usedQuantity，以及有限额时的 monthlyIssueLimit / remainingQuantity；无限额时这两个空字段按全站 non_null 约定省略。剩余额度最少为零。累计仍保留，清空或降低限额不会重置当月使用量。

以用户 + 材料 + 后端自然月为唯一键，同材料不同批次共享额度。系统业务时区默认 Asia/Shanghai，BUSINESS_TIME_ZONE 可配置；浏览器时区、请求月份不参与计算。正好达到上限允许，超出拒绝；无限额时仍累计真实用量，以便后续配置。

### 与库存流水 / 二维码的兼容

GET /api/stock-transactions 增加 referenceType=INBOUND|ISSUE，orderId 为对应类型的单据 ID，原 IN 的字段和关联不变。OUT 记录关联到 issue_order 与具体 issue_order_line，包含项目 / 用途与准确的批次前后余额。

原 inventory 菜单矩阵不变：管理员 / 实验员看全部；教师能看 IN 与本人的 OUT；采购员看 IN。不能通过库存流水旁路读取他人的私有领用信息。学生从本人领用详情读取自己的关联流水，仍不开放原库存菜单。工作台动态继续仅展示公开入库动态，不泄露私人领用用途。

批次详情追加 materialStatus，原二维码地址不变；可领用用户在 ACTIVE 且余额大于零时看到领用入口。SPA 登录恢复保留目标路径与查询参数。

PDF 由后端 PDFBox 使用随 JAR 内嵌的开源中文字体生成，A4 自动换行 / 分页，内容取自单据快照，签字栏供手写。无外部生成服务，无 JWT / 密码进入文件。字体派生来源与 OFL 授权见 backend/src/main/resources/fonts/。

## 云适配兼容说明

本轮不改变 Sprint 01–06 的 API 契约，不增加生产管理员注册 / 初始化接口。受控首次初始化只读取后端安全环境变量，默认关闭。

`POST /api/scraps/attachments` 与 `GET /api/scraps/attachments/{id}` 在 local / supabase 模式下沿用原角色、归属、类型及大小校验；Supabase 对象由后端鉴权代理，不返回公开图片 URL 或服务端 Storage key。数据库继续保存原 UUID 对象标识，查询接口继续过滤 storage_key / sha256。Storage 上游故障明确失败，不伪装成上传成功。

现行云环境变量及真实验证边界见 [部署说明](DEPLOYMENT.md)和[云适配验证记录](CLOUD-DEPLOYMENT-01.md)。云端集成尚未执行，原本地存储与认证流程保留。


