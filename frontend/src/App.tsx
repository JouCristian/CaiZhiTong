import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { MenuProps } from 'antd'
import {
  Alert, Button, Drawer, Form, Input, Menu, Result, Skeleton, Tooltip,
} from 'antd'
import {
  ArrowRightOutlined, CheckOutlined, ClockCircleOutlined, CompassOutlined,
  DeploymentUnitOutlined, FileTextOutlined, FolderOpenOutlined,
  InfoCircleOutlined, LockOutlined, MenuFoldOutlined, MenuOutlined,
  MenuUnfoldOutlined, SafetyCertificateOutlined, UserOutlined,
} from '@ant-design/icons'
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { request } from './api'
import type { Overview } from './api'
import { useAuth } from './auth'
import { modules, roleNames } from './modules'
import { MaterialsPage } from './materials/MaterialsPage'
import { AccountMenu } from './AccountMenu'
import { InventoryPage } from './stock/InventoryPage'
import { InboundPage } from './stock/InboundPage'
import { BatchPage } from './stock/BatchViews'
import { IssuesPage } from './issues/IssuesPage'
import { LifecyclePage } from './operations/LifecyclePage'
import { AlertsPage } from './operations/AlertsPage'
import { ReportsPage } from './operations/ReportsPage'
import { UsersPage } from './admin/UsersPage'
import { SettingsPage } from './admin/SettingsPage'
import { DashboardInsights } from './dashboard/DashboardInsights'
import { useSystem } from './system'
import { dateTime } from './materials/types'
import { GithubLogin, OAuthCallback } from './OAuth'

function Brand({ compact = false }: { compact?: boolean }) {
  const { info } = useSystem()
  return <div className="brand"><span className="brand-symbol"><DeploymentUnitOutlined /></span>{!compact && <div><strong title={info.systemName}>{info.systemName}</strong><span title={info.systemDescription}>{info.systemDescription}</span></div>}</div>
}
function MaterialArt({ small = false }: { small?: boolean }) {
  return <div className={small ? 'material-art small' : 'material-art'} aria-hidden="true">
    <div className="material-orbit" /><div className="material-plane back" /><div className="material-plane middle" /><div className="material-plane front"><DeploymentUnitOutlined /></div>
    <div className="material-caption"><span />材料 · 有序连接</div>
  </div>
}
function LoadingScreen() {
  return <div className="loading-screen"><Brand /><div className="loading-content"><Skeleton active paragraph={{ rows: 3 }} /></div><span>正在恢复工作空间…</span></div>
}
function Protected() {
  const { user, loading, error, restore, logout, loggedOut } = useAuth()
  const location = useLocation()
  if (loading) return <LoadingScreen />
  if (error) return <div className="standalone-result"><Result status="warning" title="暂时无法恢复登录" subTitle={error} extra={<><Button type="primary" onClick={() => void restore()}>重新连接</Button><Button onClick={logout}>返回登录</Button></>} /></div>
  return user ? <Outlet /> : <Navigate to="/login" replace state={loggedOut ? undefined : { from: location.pathname + location.search }} />
}
function Login() {
  const { info } = useSystem()
  const { user, loading, login, expired } = useAuth()
  const [submitting, setSubmitting] = useState(false)
  const [githubAvailable, setGithubAvailable] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [waiting, setWaiting] = useState(false)
  useEffect(() => {
    setWaiting(false)
    if (!submitting) return
    const timer = window.setTimeout(() => setWaiting(true), 10000)
    return () => window.clearTimeout(timer)
  }, [submitting])
  const location = useLocation()
  const navigate = useNavigate()
  if (loading) return <LoadingScreen />
  const from = (location.state as { from?: string } | null)?.from
  const target = from && from.startsWith('/') && !from.startsWith('//') && from !== '/login' ? from : '/dashboard'
  if (user) return <Navigate to={target} replace />
  async function submit(values: { username: string; password: string }) {
    setError(''); setSubmitting(true)
    try { await login(values.username.trim(), values.password); navigate(target, { replace: true }) }
    catch (e) { setError((e as Error).message) }
    finally { setSubmitting(false) }
  }
  return <main className="login-page">
    <section className="login-story"><Brand /><div className="login-story-content"><span className="quiet-label">让每一份材料，各得其所。</span><h1>连接材料，<br />也连接每一次探索。</h1><p>从材料档案到实验现场，<br />为有序、高效的实验室管理而设计。</p><MaterialArt /></div><div className="login-story-footer"><SafetyCertificateOutlined /> 清晰的权限 · 可靠的连接</div></section>
    <section className="login-panel"><div className="login-form-wrap"><div className="mobile-brand"><Brand /></div><div className="login-heading"><span className="login-glyph"><LockOutlined /></span><h2>欢迎回到{info.systemName}</h2><p>登录账户，进入你的材料工作空间。</p></div>
      {expired && !error && <Alert type="info" showIcon message="登录已过期，请重新登录" className="form-alert" />}
      {error && <Alert type="error" showIcon message={error} className="form-alert" />}
      {waiting && <Alert type="info" showIcon message="正在连接服务，请稍候" description="首次访问或长时间未使用后，连接可能需要几分钟，请勿重复提交。" className="form-alert" />}
      <GithubLogin target={target} onAvailability={setGithubAvailable} />
      {githubAvailable && <Button type="text" block className="password-login-toggle" onClick={() => setShowPassword(value => !value)} aria-expanded={showPassword}>{showPassword ? '收起账号密码登录' : '使用账号密码登录'}</Button>}
      {(!githubAvailable || showPassword) && <Form layout="vertical" requiredMark={false} onFinish={submit} autoComplete="on" size="large">
        <Form.Item label="用户名" name="username" rules={[{ required: true, whitespace: true, message: '请输入用户名' }, { max: 64, message: '用户名最多 64 个字符' }]}><Input prefix={<UserOutlined />} placeholder="请输入用户名" autoComplete="username" maxLength={64} /></Form.Item>
        <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }, { max: 72, message: '密码最多 72 个字符' }]}><Input.Password prefix={<LockOutlined />} placeholder="请输入密码" autoComplete="current-password" maxLength={72} /></Form.Item>
        <Button type="primary" htmlType="submit" block size="large" aria-label="登录工作空间" loading={submitting} className="login-submit">登录工作空间 <ArrowRightOutlined aria-hidden /></Button>
      </Form>}
      <div className="login-note"><InfoCircleOutlined />{import.meta.env.PROD ? <p>请使用管理员分配的账户登录。<br />如需访问权限，请联系系统管理员。</p> : <p>本地演示支持管理员、实验员和学生账户。<br />请使用开发环境中生成的凭据登录。</p>}</div>
      <div className="login-security"><SafetyCertificateOutlined /> 密码经加密存储，访问受角色权限保护</div>
    </div><footer>材智通 CaiZhiTong <span>·</span> 智慧材料库数字化管理系统</footer></section>
  </main>
}
function Shell() {
  const { info } = useSystem()
  const { user, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const current = modules.find(m => location.pathname === '/' + m.key) || (location.pathname.startsWith('/stock/batches/') ? { ...modules[1], title: '批次详情' } : undefined)
  useEffect(() => { document.title = (current?.title || '页面未找到') + ' · ' + info.systemName; setMobileOpen(false) }, [location.pathname, current?.title, info.systemName])
  if (!user) return null
  const allowed = modules.filter(m => user.modules.includes(m.key))
  const groups = [
    { label: '工作空间', keys: ['dashboard'] },
    { label: '材料与流转', keys: ['materials', 'inventory', 'inbound', 'requisitions', 'returns', 'alerts', 'reports'] },
    { label: '系统管理', keys: ['users', 'settings'] },
  ]
  const items: MenuProps['items'] = groups.flatMap<NonNullable<MenuProps['items']>[number]>(group => {
    const children = allowed.filter(m => group.keys.includes(m.key)).map(m => ({ key: m.key, icon: <m.icon />, label: m.title }))
    if (!children.length) return []
    return collapsed ? children : [{ type: 'group' as const, label: group.label, children }]
  })
  const nav = <><Link to="/dashboard" className="brand-link" aria-label="材智通工作台"><Brand compact={collapsed} /></Link><nav aria-label="主导航"><Menu mode="inline" inlineCollapsed={collapsed} selectedKeys={current ? [current.key] : []} items={items} onClick={({ key }) => navigate('/' + key)} /></nav><div className="sidebar-bottom">{!collapsed && <div className="workspace-version"><span className="version-dot" /><div><strong>实验室工作空间</strong><span>Sprint 06 · 完整工作空间</span></div></div>}<Tooltip title={collapsed ? '展开侧边栏' : '收起侧边栏'}><Button className="collapse-button" type="text" aria-label={collapsed ? '展开侧边栏' : '收起侧边栏'} icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => setCollapsed(!collapsed)} /></Tooltip></div></>
  return <div className={'app-shell' + (collapsed ? ' is-collapsed' : '')}><a className="skip-link" href="#main-content">跳转至内容</a><aside className="sidebar">{nav}</aside>
    <Drawer title="工作空间导航" open={mobileOpen} onClose={() => setMobileOpen(false)} placement="left" styles={{ body: { padding: 0 } }} width={280}><div className="drawer-nav">{nav}</div></Drawer>
    <div className="app-body"><header className="toolbar"><div className="toolbar-left"><Button type="text" className="mobile-menu" aria-label="打开导航" icon={<MenuOutlined />} onClick={() => { setCollapsed(false); setMobileOpen(true) }} /><span className="toolbar-workspace">材料工作空间</span><span className="toolbar-separator">/</span><span>{current?.title || '页面未找到'}</span></div><AccountMenu user={user} onLogout={() => { logout(); navigate('/login', { replace: true }) }} /></header>
      <main id="main-content" className="main-content"><Outlet /></main><footer className="app-footer"><span>材智通 · 为实验室的每一天</span><span>Sprint 06 <span className="footer-dot">·</span> v{info.version}</span></footer></div></div>
}
function PageHeading({ title, description, extra }: { title: string; description: string; extra?: ReactNode }) {
  return <div className="page-heading"><div><h1>{title}</h1><p>{description}</p></div>{extra}</div>
}
function Dashboard() {
  const { user } = useAuth()
  const [data, setData] = useState<Overview | null>(null)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const navigate = useNavigate()
  useEffect(() => {
    let active = true
    setError(''); setData(null)
    request<Overview>('/api/workspace/overview').then(d => { if (active) setData(d) }).catch(e => { if (active) setError((e as Error).message) })
    return () => { active = false }
  }, [retry])
  const date = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
  const hour = new Date().getHours()
  const greeting = hour < 11 ? '上午好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'
  const quick = modules.filter(m => ['materials', 'inbound', 'requisitions'].includes(m.key) && user?.modules.includes(m.key))
  const metrics = [
    { key: 'materials' as const, label: '材料种类', icon: modules[1].icon, unit: '种' },
    { key: 'stock' as const, label: '有库存材料', icon: modules[2].icon, unit: '种' },
    { key: 'pending' as const, label: '待处理申请', icon: FileTextOutlined, unit: '项' },
    { key: 'alerts' as const, label: '库存预警', icon: modules[6].icon, unit: '项' },
  ]
  return <div className="page-enter"><PageHeading title="工作台" description="你的材料库，一目了然。" extra={<span className="today"><ClockCircleOutlined />{date}</span>} />
    <section className="welcome-panel"><div className="welcome-copy"><span className="workspace-tag"><span /> 我的工作空间</span><h2>{greeting}，{user?.displayName}。</h2><p>从这里开始，让材料管理井然有序。</p><Button type="primary" onClick={() => navigate('/materials')}>浏览材料库 <ArrowRightOutlined /></Button><div className="welcome-role"><SafetyCertificateOutlined />{user?.roles.map(r => roleNames[r] || r).join('、')}<span>·</span> 权限已同步</div></div><MaterialArt small /></section>
    <div className="section-heading"><h2>材料库概览</h2><span>材料与库存 · 实时统计</span></div>
    {error ? <Alert type="error" showIcon message="概览加载失败" description={error} action={<Button onClick={() => setRetry(r => r + 1)}>重试</Button>} /> : <section className="metrics-panel" aria-label="关键指标">{metrics.map(m => <div className="metric" key={m.key}><div className="metric-label"><m.icon /><span>{m.label}</span></div>{data ? <div className="metric-value">{data.metrics[m.key]}<span>{m.unit}</span></div> : <Skeleton.Input active size="small" />}<span className="metric-note">{m.key === 'materials' ? '真实档案数，含归档记录' : m.key === 'stock' ? '批次余额大于零的材料' : m.key === 'alerts' ? '低于安全库存的材料' : '本轮即时领用，无审批队列'}</span></div>)}</section>}
    <DashboardInsights /><div className="dashboard-grid"><section className="surface quick-panel"><div className="panel-heading"><h2>快捷入口</h2><span>常用工作</span></div><div className="quick-list">{quick.map(m => <Link to={'/' + m.key} key={m.key} className="quick-item"><span className={'quick-icon ' + m.key}><m.icon /></span><div><h3>{m.title}</h3><p>{m.description}</p></div><ArrowRightOutlined /></Link>)}</div></section>
      <section className="surface activity-panel"><div className="panel-heading"><h2>最近动态</h2><span className="subtle-tag">材料流转</span></div>{error ? <div className="empty-activity"><InfoCircleOutlined /><h3>暂时无法读取动态</h3><p>请重试加载概览</p></div> : !data ? <Skeleton active className="activity-skeleton" /> : data.activities.length ? <ul className="activity-records">{data.activities.map(a => <li key={a.id}><span><CheckOutlined /></span><div><strong>{a.title}</strong><p>{a.detail}</p><time>{dateTime(a.createdAt)}</time></div></li>)}</ul> : <div className="empty-activity"><span className="empty-activity-icon"><ClockCircleOutlined /></span><h3>一切，从这里开始</h3><p>暂无材料流转记录。<br />入库成功后，最新流转将在这里呈现。</p><span className="empty-caption">等待第一条材料记录</span></div>}</section></div>
    <section className="progress-banner"><span className="progress-icon"><CompassOutlined /></span><div><h3>工作空间已就绪</h3><p>材料流转、用户权限与全局设置已就绪。数据按真实业务记录汇总。</p></div><span className="progress-status"><CheckOutlined /> Sprint 06</span></section>
  </div>
}
function Forbidden() { return <div className="surface result-panel"><Result status="403" title="此页面需要更多权限" subTitle="当前账户无权访问。你可以返回工作台，或联系系统管理员。" extra={<Link to="/dashboard"><Button type="primary">返回工作台</Button></Link>} /></div> }
function RoleGuard({ moduleKey, children }: { moduleKey: string; children: ReactNode }) {
  const { user } = useAuth()
  return user?.modules.includes(moduleKey) ? children : <Forbidden />
}
function NotFound() { return <div className="surface result-panel"><Result icon={<FolderOpenOutlined />} title="没有找到这个页面" subTitle="页面地址可能已更改，请检查地址或返回工作台。" extra={<Link to="/dashboard"><Button type="primary">返回工作台</Button></Link>} /></div> }
export function App() {
  return <Routes><Route path="/login" element={<Login />} /><Route path="/oauth/callback" element={<OAuthCallback />} /><Route element={<Protected />}><Route element={<Shell />}><Route index element={<Navigate to="/dashboard" replace />} /><Route path="/dashboard" element={<RoleGuard moduleKey="dashboard"><Dashboard /></RoleGuard>} />{modules.filter(m => m.key !== 'dashboard').map(m => <Route key={m.key} path={'/' + m.key} element={<RoleGuard moduleKey={m.key}>{m.key === 'materials' ? <MaterialsPage /> : m.key === 'inventory' ? <InventoryPage /> : m.key === 'inbound' ? <InboundPage /> : m.key === 'requisitions' ? <IssuesPage /> : m.key === 'returns' ? <LifecyclePage /> : m.key === 'alerts' ? <AlertsPage /> : m.key === 'reports' ? <ReportsPage /> : m.key === 'users' ? <UsersPage /> : <SettingsPage />}</RoleGuard>} />)}<Route path="/stock/batches/:code" element={<RoleGuard moduleKey="materials"><BatchPage /></RoleGuard>} /><Route path="*" element={<NotFound />} /></Route></Route></Routes>
}



