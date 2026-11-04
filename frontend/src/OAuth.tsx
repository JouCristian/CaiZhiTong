import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Result, Skeleton } from 'antd'
import { GithubOutlined } from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { apiUrl, request } from './api'
import { useAuth } from './auth'

const FLOW = 'caizhitong.oauth.flow'
type Flow = { verifier: string; target: string; started: number }
function targetPath(path?: string) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') && !path.includes('\\') && !path.startsWith('/oauth/') && !path.startsWith('/login') ? path : '/dashboard'
}
function encode(bytes: Uint8Array) { return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '') }
export function GithubLogin({ target, onAvailability }: { target: string; onAvailability: (available: boolean) => void }) {
  const [available, setAvailable] = useState(false), [loading, setLoading] = useState(true), [starting, setStarting] = useState(false), [error, setError] = useState('')
  useEffect(() => {
    let active = true
    request<{ githubEnabled: boolean }>('/api/auth/oauth/config', {}, false)
      .then(data => { if (active) { setAvailable(data.githubEnabled); onAvailability(data.githubEnabled) } })
      .catch(() => { if (active) setError('暂时无法连接 GitHub 登录服务，可稍后重试或使用账号密码。') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [onAvailability])
  async function start() {
    setStarting(true); setError('')
    try {
      const verifier = encode(crypto.getRandomValues(new Uint8Array(32)))
      const challenge = encode(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))))
      sessionStorage.setItem(FLOW, JSON.stringify({ verifier, target: targetPath(target), started: Date.now() } satisfies Flow))
      window.location.assign(apiUrl('/oauth2/authorization/github') + '?challenge=' + challenge)
    } catch { setError('无法发起安全授权，请使用 HTTPS 或本机地址打开网站后重试。'); setStarting(false) }
  }
  return <div className="github-login">
    <Button type="primary" size="large" block aria-label="继续使用 GitHub" icon={<GithubOutlined aria-hidden />} loading={starting || loading} disabled={!available || loading} onClick={() => void start()}>继续使用 GitHub</Button>
    {error ? <Alert type="warning" showIcon message={error} /> : <p>{loading ? '正在连接登录服务…' : available ? '首次登录创建只读体验账户，无需提供邮箱。' : 'GitHub 登录尚未启用，仍可使用账号密码登录。'}</p>}
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
