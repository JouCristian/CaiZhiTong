# UI 精修 01：登录视觉、侧栏与抽屉

日期：2026-11-05。分支：`codex/ui-motion-polish`。本轮为前端改进，不修改后端、数据库迁移及业务权限。用户完成本地视觉验收后已授权 PR 合并和生产上线，实际发布结果补充在本记录末尾。

## 设计与实现

沿用白、浅灰与系统蓝的 Apple 风格。后台以查找、录入和阅读为主，较强的品牌动效限定在登录展示区。

- Particle Text：登录左侧“连接材料”标题，保留普通文字作为首次绘制前的回退和可访问标题。
- Text Type：标题下的短句。固定容器高度，避免打字时布局跳动。
- Circular Text：GitHub 登录按钮中的环形文字，28 秒一圈、悬停暂停；保留实际 OAuth 点击、错误与等待处理。
- Curved Loop：左侧底部低速曲线装饰，不捕获指针，不承载业务信息。
- Dither Veil：自有材料连接标识的网点与指针揭示效果。素材为本地立体风格 SVG，并非可旋转的三维模型；没有引用示例人像或远程照片。
- Staggered Menu：借鉴错峰与分层过渡，继续使用现有 Ant Design 菜单和抽屉，保留键盘、遮罩、权限及业务回调。

React Bits 来源锁定 `DavidHDev/react-bits@4dc7b5b7772e1217c8fb8d2a7aa074302180e5b4`，使用 TS / CSS 版本，保留 [原许可与说明](../frontend/src/motion/reactbits/NOTICE.md)。新增精确锁定依赖 GSAP 3.15.1、Motion 14.9.1、OGL 1.0.11；未引入新的 UI 框架。

## 修复

原收起菜单固定宽度叠加导航内边距，产生横向滚动。现在展开 256px、收起 80px，菜单随容器取宽度，图标居中，品牌与页脚文字收放，短窗口保留纵向滚动。分组保持稳定，不再用另一份菜单结构替换。

原条件渲染在关闭时立即卸载抽屉。`DrawerPresence` 保留即将退出的内容，`MotionDrawer` 通过关闭状态运行原生面板过渡，收到结束回调后卸载；超时兜底避免残留。退出面板立即设置 inert / aria-hidden，防止保存后打开详情时出现两个可操作对话框。“保存 → 详情”时保护新面板的焦点，避免旧抽屉恢复触发器焦点时抢走新面板的焦点。保留原加载、表单校验、未知结果重试和服务端权限规则。

登录效果拆分为两个延迟加载模块，仅在宽度至少 960px、页面可见且未要求减少动态效果时启用。移动端与减少动态效果使用静态标题、标识和文字环，不加载装饰画布；WebGL2 不可用、纹理未加载或上下文丢失时保留本地 SVG。Dither Veil 像素比最多 1.5，静止时暂停渲染；离开登录页清理监听、计时与图形上下文。粒子预算上限 5,200，针对短中文标题提高采样下限，保留笔画；标识背景按 shader 的加法亮度单位校准，避免纯白或整块背景网点。

## 验证

测试使用独立 `caizhitong_v070_demo_e2e_test` 数据库及本机 Mock OAuth Provider，不访问生产业务数据或真实 GitHub 账户。

业务浏览器最终完整回归 **70/70 通过**（4.4 分钟），保留原 64 项，新增桌面 / 手机共 6 项侧栏、抽屉和减少动态效果测试。抽屉测试等待实际进入完成，再轮询退出过程中的屏幕内中间位置，确认先滑出、后卸载，并验证重复打开及焦点恢复。另有针对抽屉与领用创建流程的专项 **8/8 通过**（36 秒）。

OAuth / 视觉最终回归 **18/18 通过**（36.4 秒），保留原 14 项认证 / Demo 隔离，新增桌面 / 手机共 4 项登录视觉与 WebGL 回退。覆盖 WebGL 不可用，以及已运行的 WebGL 上下文丢失后重新悬停仍保留静态标识。使用生产构建、本机 Chrome、回环 Mock Provider 和真实隔离 PostgreSQL，不代表本轮重新执行了真实 GitHub 授权。

前端 `npm run typecheck` 与 `npm run build` 通过。五组真实 HTTP 冒烟脚本（materials、stock、issues、lifecycle、administration）全部通过，覆盖数据库持久化、原权限、库存链路与设置。后端源码和迁移没有修改；本轮未重新执行 Maven，不把此前的 195 项结果算作本轮执行。

生产构建为两个独立延迟模块：登录展示约 168.2 kB（gzip 60.7 kB），环形入口约 142.3 kB（gzip 48.5 kB）。主模块约 411.7 kB（gzip 130.6 kB）。Ant Design 公共块仍约 1.1 MB，构建保留其体积提示，本轮未做全站拆包重构。

前几轮发现并修正：测试下载的 Chromium 无法启动，改用本机安装 Chrome，并新增可选 `E2E_BROWSER_EXECUTABLE`；旧样式导入顺序覆盖侧栏宽度；退出面板仍出现在辅助访问树；新增测试需要兼容 Ant Design 对两个汉字按钮自动插入的空格，以及 rc-motion 的多帧退出准备时序。重复测试累积的数据曾使一项 TOP10 断言失败（69/70）；使用原脚本重置独立测试业务夹具后，最终完整 70 项通过。没有跳过业务用例或放宽权限断言。

本机复验使用 `scripts/start-backend.ps1 -E2E` 与 `scripts/start-e2e-frontend.ps1` 启动服务；OAuth 阶段改为 `-E2E -OAuthMock` 并运行 `scripts/mock-github.mjs`。设置 `E2E_BASE_URL=http://127.0.0.1:5180`、`E2E_ISOLATED=1`、`E2E_BROWSER_EXECUTABLE` 为本机 Chrome 路径后，执行 `npx playwright test --config frontend/playwright.config.ts --reporter line`。业务阶段 `E2E_OAUTH=0`，OAuth 阶段为 `1`；业务阶段开始前执行 `scripts/portable-postgres.ps1 e2e-db -ResetE2E`。标准集成入口仍是 `scripts/test-browser.ps1` 及其 `-OAuthMock` 选项。本次未运行远程 CI。

## 真实本地截图

### 人工复验修正：右侧抽屉进入闪现

用户指出右侧抽屉出现闪现。逐帧复现确认：对内容 wrapper 全局设置 `transition: ... !important`，覆盖了 rc-motion 进入准备阶段的 `transition: none`。准备帧先显示在最终位置，再短暂偏移返回，造成闪出。遮罩同样存在准备阶段被覆盖的风险。

改为只在 appear / enter / leave 的 active 阶段设置自定义过渡，保留组件的初始离屏定位。1440px 桌面、640px 面板实测：修正前第一帧 x=800；修正后从 x=1440 连续滑入到 x=800，第二次打开结果一致。面板位移和透明度使用相同持续时间，避免其中一个属性提前结束触发卸载。

原抽屉回归扩展为进入 / 退出逐帧检查，要求第一帧离屏、至少五个不同的中间位置、连续向内移动，保留退出卸载与焦点恢复断言。测试登录辅助等待 OAuth 配置加载完成；程序化采样触发前显式聚焦按钮，以保留正常点击的焦点行为。修正后专项桌面 / 手机 **6/6 通过**（21 秒），`npm run build`（含 TypeScript）通过；本次未重复运行此前完整 70/18 项或 Maven。预览使用本地隔离库，未 Push 或部署。

- [登录页 · 桌面](screenshots/ui-polish-login-desktop.png)
- [登录页 · 手机](screenshots/ui-polish-login-mobile.png)
- [侧栏收起](screenshots/ui-polish-sidebar-collapsed.png)
- [侧栏展开 · 短窗口](screenshots/ui-polish-sidebar-expanded.png)
- [手机导航](screenshots/ui-polish-sidebar-mobile.png)

截图来自本机 Chrome，数据为独立测试库的合成样本；静态图片无法呈现聚合、指针揭示与关闭过渡，请在本地页面验收动画。

## 本地人工验收

1. 打开本地登录页，桌面观察标题聚合、短句、底部曲线；将指针移到标识上，查看网点揭示（需要支持 WebGL 的设备）。
2. GitHub 入口保持真实认证行为；隔离预览使用本机 Mock Provider。展开账号密码入口，确认表单与错误提示正常。
3. 登录后连续收起 / 展开侧栏，检查无横向滚动；缩短窗口高度，确认所有菜单可纵向访问。
4. 材料新增 / 详情、库存批次、入库、领用、归还、报废、用户详情等抽屉检查进入、关闭及重复打开。
5. 手机检查导航抽屉、登录与表单；开启系统“减少动态效果”，确认无装饰画布且功能完整。

## 发布边界

初次本地交付时未推送或部署。用户随后确认抽屉修复正常并授权合并上线。发布准备增加 Vercel 的 `brand/` 静态路径例外，避免新 SVG 标识进入 SPA 重写；CI 在原 OAuth / Demo 18 项后执行 6 项侧栏 / 抽屉回归。沿用 v0.7.0 与现有生产平台，不创建 Release、付费资源或新项目；上线结果以实际 CI 与平台状态为准。

## 生产发布 · 2026-11-05

用户完成视觉验收并授权上线后，发布审计覆盖 365 个候选 / 跟踪文件及 Git 对象，无检测到的秘密、危险路径或失效 Markdown 链接；原 V1–V7 和后端业务代码没有修改。

- 功能提交：`c460e2db1b0ed3f59f268f6024efe9f8d9922a37`。
- [PR #2](https://github.com/JouCristian/CaiZhiTong/pull/2) 核对 Head SHA 后正常合并，保留历史；main 合并提交 `df4778f0ab9361cfa39649806f954493c1f41ff8`。
- [Push CI 41953001056](https://github.com/JouCristian/CaiZhiTong/actions/runs/41953001056) 与 [PR CI 41953034839](https://github.com/JouCristian/CaiZhiTong/actions/runs/41953034839) 实际成功。远程 Maven **195/195**，OAuth / 视觉 **18/18**、侧栏 / 抽屉 **6/6**，npm ci / typecheck / build、Docker 构建与 512 MiB 容器运行均通过。
- [合并后 main CI 41953820082](https://github.com/JouCristian/CaiZhiTong/actions/runs/41953820082) 同样实际成功。
- Render 自动部署 `dep-dblucoks728c73f5c7c0` 为 **live**，运行合并提交，2026-11-05 02:16:31 UTC 完成；HTTPS 健康 UP。
- Vercel 自动部署 `dpl_9hMZttaFjAYJvEFHPTyCwxYwbkbX` 为 **READY / production**。通过正式域名解析接口核验 `caizhitong-eight.vercel.app` 指向上述合并提交，未将预览部署算作上线。

正式前端 https://caizhitong-eight.vercel.app，后端 https://caizhitong-api.onrender.com。上线后 **29 项**实际检查通过：

- HTTPS 健康、OAuth enabled、未登录 API 401；新品牌 SVG 返回 `image/svg+xml`，不被 SPA 重写。
- 真实桌面 / 手机登录布局、粒子与字环加载、手机静态回退，无横向溢出或未捕获页面错误。
- 原生产管理员密码登录、原正式材料读取正常；原验收批次余额和 Supabase 私有附件 SHA-256 保持一致。
- 侧栏收起 80px / 展开 256px，收起无横向溢出；正式站桌面 / 手机各连续打开两次材料抽屉，第一帧离屏、多帧连续滑入，取消后移除面板，未保存任何业务数据。

真实截图：[正式桌面登录](screenshots/ui-polish-cloud-login-desktop.png)、[正式手机登录](screenshots/ui-polish-cloud-login-mobile.png)。截图在输入凭据前采集，不包含密码、Token 或私人账户资料。首次本机验收脚本因原生 Node fetch 未采用既有网络代理而超时，使用环境代理后重新执行全部通过，未修改生产服务来规避客户端网络问题。

本次保留 GitHub OAuth 的正式配置，并验证入口可用和后台开关为 true；未重新操作真实 GitHub 授权，既有持有人真实授权记录仍见 [Cloud Deployment 03](CLOUD-DEPLOYMENT-03.md)。生产检查只读取业务，密码登录按原逻辑更新最近登录时间。没有创建 Release、云项目或付费资源。
