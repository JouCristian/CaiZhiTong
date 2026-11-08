import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Result, Skeleton } from 'antd'
import { GithubOutlined } from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { apiUrl } from './api'
import { useAuth } from './auth'
import { OrbitRing } from './motion/OrbitRing'
import { useGithubAvailability } from './useGithubAvailability'

const FLOW = 'caizhitong.oauth.flow'
type Flow = { verifier: string; target: string; started: number }
function targetPath(path?: string) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') && !path.includes('\\') && !path.startsWith('/oauth/') && !path.startsWith('/login') ? path : '/dashboard'
}
function encode(bytes: Uint8Array) { return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '') }
export function GithubLogin({ target, onAvailability }: { target: string; onAvailability: (available: boolean) => void }) {
  const connection = useGithubAvailability(onAvailability)
  const available = connection.phase === 'available'
  const [starting, setStarting] = useState(false), [error, setError] = useState('')
  const startLock = useRef(false), mounted = useRef(false)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  async function start() {
    if (!available || startLock.current) return
    startLock.current = true
    setStarting(true); setError('')
    try {
      const verifier = encode(crypto.getRandomValues(new Uint8Array(32)))
      const challenge = encode(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))))
      if (!mounted.current) return
      sessionStorage.setItem(FLOW, JSON.stringify({ verifier, target: targetPath(target), started: Date.now() } satisfies Flow))
      window.location.assign(apiUrl('/oauth2/authorization/github') + '?challenge=' + challenge)
    } catch {
      startLock.current = false
      if (mounted.current) { setError('无法发起安全授权，请使用 HTTPS 或本机地址打开网站后重试。'); setStarting(false) }
    }
  }
  return <div className="github-login">
    <Button size="large" block className="github-orbit-action" aria-label="继续使用 GitHub" loading={starting} disabled={!available || starting} onClick={() => void start()}><span className="github-action-copy"><strong>继续使用 GitHub</strong><span>进入公开演示空间</span></span><span className="github-action-badge"><OrbitRing /><span className="github-action-symbol"><GithubOutlined aria-hidden /></span></span></Button>
    {error && <Alert type="warning" showIcon message={error} />}
    {available ? <p>首次登录创建只读体验账户，无需提供邮箱。</p>
      : connection.phase === 'disabled' ? <p role="status">GitHub 登录尚未启用，仍可使用账号密码登录。</p>
      : <div className="oauth-connection" role="status" aria-live="polite">
        <strong>{connection.phase === 'connecting' ? '正在检测登录服务' : connection.phase === 'waking' ? '服务暂未就绪，可能正在唤醒' : connection.phase === 'unreachable' ? '网络连接失败' : '登录服务响应异常'}</strong>
        <p>{connection.phase === 'connecting' ? '单次检测最多 8 秒；账号密码入口仍可使用。'
          : connection.phase === 'waking' ? '刚才的检测超时或服务暂不可用。若后端休眠，唤醒可能需要 2–3 分钟。'
          : connection.phase === 'unreachable' ? '暂时无法连接后端，请检查网络后重试。此状态不表示 GitHub 登录被关闭。'
          : '服务返回了异常响应。请重新连接，若持续出现请联系维护者。'}</p>
        <div className="oauth-connection-actions"><span>{connection.checking ? '正在检测…' : connection.scheduled ? '将自动重新检测' : '已停止自动重试，可手动重新连接'}</span><Button size="small" disabled={connection.checking} onClick={connection.retry}>重新连接</Button></div>
      </div>}
  </div>
}
const errors: Record<string, string> = {
  cancelled: '你已取消 GitHub 授权，可以重新尝试。',
  state_invalid: '授权会话已过期或不匹配，请重新发起登录。',
  disabled: '此账户已被停用，请联系系统管理员。',
  failed: 'GitHub 授权未完成，请稍后重试。',
}
export function OAuthCallback() {
  const { completeOAuth } = useAuth(), navigate = useNavigate(), location = useLocation()
  const [error, setError] = useState('')
  const pending = useRef<{ fragment: string; promise: Promise<string> } | null>(null)
  useEffect(() => {
    let active = true
    if (!pending.current || (location.hash && pending.current.fragment !== location.hash)) {
      setError('')
      pending.current = { fragment: location.hash, promise: (async () => {
      const fragment = new URLSearchParams(window.location.hash.slice(1))
      window.history.replaceState(null, '', window.location.pathname)
      const raw = sessionStorage.getItem(FLOW)
      sessionStorage.removeItem(FLOW)
      if (fragment.has('error')) throw new Error(errors[fragment.get('error')!] || errors.failed)
      let flow: Flow | undefined
      try { flow = raw ? JSON.parse(raw) as Flow : undefined } catch { /* A missing browser verifier cannot be exchanged. */ }
      const code = fragment.get('code')
      if (!code || !/^[A-Za-z0-9_-]{43}$/.test(code) || !flow || !/^[A-Za-z0-9_-]{43}$/.test(flow.verifier) || !Number.isFinite(flow.started) || Date.now() - flow.started > 10 * 60 * 1000 || flow.started > Date.now()) throw new Error('登录上下文缺失或已过期，请从本页重新发起 GitHub 登录。')
      const user = await completeOAuth(code, flow.verifier)
      const target = targetPath(flow.target)
      return user.modules.includes('demo/dashboard') && !target.startsWith('/demo/') ? '/demo/dashboard' : target
      })() }
    }
    pending.current.promise.then(target => { if (active) navigate(target, { replace: true }) }).catch(e => { if (active) setError((e as Error).message) })
    return () => { active = false }
  }, [completeOAuth, navigate, location.hash])
  return <main className="standalone-result oauth-result">{error
    ? <Result status="warning" title="未能完成 GitHub 登录" subTitle={error} extra={<Button type="primary" onClick={() => navigate('/login', { replace: true })}>返回登录</Button>} />
    : <section className="surface oauth-progress"><GithubOutlined /><h1>正在完成安全登录</h1><p>正在确认账户与只读体验权限，请稍候。</p><Skeleton active paragraph={{ rows: 2 }} /></section>}</main>
}
