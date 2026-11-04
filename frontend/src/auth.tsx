import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, getToken, request, setToken } from './api'
import type { LoginResult, User } from './api'
type AuthContextValue = {
  user: User | null; loading: boolean; error: string; expired: boolean; loggedOut: boolean
  login: (username: string, password: string) => Promise<void>
  completeOAuth: (code: string, verifier: string) => Promise<User>
  logout: () => void; restore: () => Promise<void>; refreshProfile: () => Promise<void>
}
const AuthContext = createContext<AuthContextValue | null>(null)
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  const [loggedOut, setLoggedOut] = useState(false)
  const restore = useCallback(async () => {
    setError(''); setLoading(true)
    if (getToken()) {
      try { setUser(await request<User>('/api/auth/me')) }
      catch (e) {
        if (!(e instanceof ApiError && e.status === 401)) setError((e as Error).message)
        setUser(null)
      }
    }
    setLoading(false)
  }, [])
  const refreshProfile = useCallback(async () => {
    const token = getToken()
    if (!token) return
    try { const profile = await request<User>('/api/auth/me'); if (token === getToken()) setUser(profile) } catch { /* 401 is handled by the shared API client; transient errors keep the current view. */ }
  }, [])
  useEffect(() => {
    const refresh = () => { void refreshProfile() }
    window.addEventListener('auth:refresh', refresh)
    window.addEventListener('focus', refresh)
    return () => { window.removeEventListener('auth:refresh', refresh); window.removeEventListener('focus', refresh) }
  }, [refreshProfile])
  useEffect(() => {
    void restore()
    const expire = () => { setUser(null); setExpired(true); setLoggedOut(false) }
    window.addEventListener('auth:expired', expire)
    return () => window.removeEventListener('auth:expired', expire)
  }, [restore])
  useEffect(() => {
    if (!user) return
    try {
      const payload = JSON.parse(atob(getToken()!.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp: number }
      const timer = window.setTimeout(() => {
        setToken(null); setUser(null); setExpired(true)
      }, Math.max(0, payload.exp * 1000 - Date.now()))
      return () => window.clearTimeout(timer)
    } catch { setToken(null); setUser(null); setExpired(true) }
  }, [user])
  const acceptLogin = useCallback((data: LoginResult) => {
    setToken(data.accessToken); setUser(data.user); setExpired(false); setLoggedOut(false); setError('')
  }, [])
  async function login(username: string, password: string) {
    acceptLogin(await request<LoginResult>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }, false))
  }
  const completeOAuth = useCallback(async (code: string, verifier: string) => {
    const data = await request<LoginResult>('/api/auth/oauth/exchange', { method: 'POST', body: JSON.stringify({ code, verifier }) }, false)
    acceptLogin(data)
    return data.user
  }, [acceptLogin])
  function logout() { setLoggedOut(true); setToken(null); setUser(null); setExpired(false); setError('') }
  return <AuthContext.Provider value={{ user, loading, error, expired, loggedOut, login, completeOAuth, logout, restore, refreshProfile }}>{children}</AuthContext.Provider>
}
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider missing')
  return context
}

