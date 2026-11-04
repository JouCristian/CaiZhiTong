export interface User {
  id: number
  username: string
  displayName: string
  roles: string[]
  modules: string[]
}
export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}
export interface Overview {
  businessAvailable: boolean
  metrics: {
    materials: number
    stock: number
    pending: number
    alerts: number
  }
  activities: {
    id: string
    title: string
    detail: string
    createdAt: string
  }[]
}
const KEY = 'caizhitong.session'
export function getToken() {
  return sessionStorage.getItem(KEY)
}
export function setToken(token: string | null) {
  if (token) sessionStorage.setItem(KEY, token)
  else sessionStorage.removeItem(KEY)
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}
const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
// Free hosting may need several minutes to wake after inactivity.
const configuredTimeout = Number(import.meta.env.VITE_API_TIMEOUT_MS)
const requestTimeout = Number.isFinite(configuredTimeout) && configuredTimeout >= 12000 && configuredTimeout <= 300000
  ? configuredTimeout : 12000
// randomUUID requires HTTPS; getRandomValues also works on local LAN HTTP.
export function newRequestId(): string {
  if (typeof globalThis.crypto.randomUUID === 'function')
    return globalThis.crypto.randomUUID()
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
export async function requestBlob(path: string, timeout = requestTimeout): Promise<Blob> {
  const token = getToken(),
    controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeout)
  try {
    const response = await fetch(base + path, {
      signal: controller.signal,
      headers: token ? { Authorization: 'Bearer ' + token } : {},
    })
    if (response.status === 401 && token === getToken()) {
      setToken(null)
      window.dispatchEvent(new Event('auth:expired'))
    }
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new ApiError(response.status, body?.message || '文件加载失败')
    }
    return await response.blob()
  } catch (e) {
    if (e instanceof ApiError) throw e
    throw new ApiError(
      0,
      controller.signal.aborted ? '文件请求超时' : '无法连接文件服务',
    )
  } finally {
    window.clearTimeout(timer)
  }
}
export async function request<T>(
  path: string,
  options: RequestInit = {},
  authenticated = true,
): Promise<T> {
  const token = authenticated ? getToken() : null
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), requestTimeout)
  try {
    const response = await fetch(base + path, {
      ...options,
      signal: controller.signal,
      headers: {
        ...(options.body && !(options.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...options.headers,
      },
    })
    const body = (await response.json().catch(() => null)) as {
      code: number
      message: string
      data: T
    } | null
    if (response.status === 401 && authenticated && token === getToken()) {
      setToken(null)
      window.dispatchEvent(new Event('auth:expired'))
    }
    if (response.status === 403 && authenticated && token === getToken()) window.dispatchEvent(new Event('auth:refresh'))
    if (!response.ok)
      throw new ApiError(
        response.status,
        body?.message || '服务暂时不可用，请稍后重试',
      )
    if (!body || body.code !== 0) throw new ApiError(502, '服务响应格式不正确')
    return body.data
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(
      0,
      controller.signal.aborted
        ? '请求超时，请稍后重试'
        : '无法连接服务，请检查后端是否启动',
    )
  } finally {
    window.clearTimeout(timer)
  }
}
