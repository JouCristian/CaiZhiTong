import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, request } from './api'

// Login capability detection must not inherit the 240-second business timeout.
export const OAUTH_PROBE_TIMEOUT_MS = 8000
const RETRY_DELAYS_MS = [5000, 15000, 30000, 45000, 45000]
type Phase = 'connecting' | 'available' | 'disabled' | 'waking' | 'unreachable' | 'unavailable'
type State = { phase: Phase; checking: boolean; scheduled: boolean; attempt: number }

export function useGithubAvailability(onAvailability: (available: boolean) => void) {
  const [cycle, setCycle] = useState(0)
  const [state, setState] = useState<State>({ phase: 'connecting', checking: true, scheduled: false, attempt: 1 })
  const callback = useRef(onAvailability)
  const checking = useRef(true)
  const cancel = useRef(() => {})
  callback.current = onAvailability
  useEffect(() => {
    let active = true
    let timer: number | undefined
    let controller: AbortController | undefined
    callback.current(false)
    async function probe(attempt: number) {
      if (!active) return
      checking.current = true
      controller = new AbortController()
      setState(previous => ({ ...previous, checking: true, scheduled: false, attempt: attempt + 1 }))
      try {
        const config = await request<{ githubEnabled: boolean }>('/api/auth/oauth/config', { signal: controller.signal }, false, OAUTH_PROBE_TIMEOUT_MS)
        if (!config || typeof config.githubEnabled !== 'boolean') throw new ApiError(502, '登录配置响应无效', 'protocol')
        if (!active) return
        callback.current(config.githubEnabled)
        setState({ phase: config.githubEnabled ? 'available' : 'disabled', checking: false, scheduled: false, attempt: attempt + 1 })
      } catch (error) {
        if (!active) return
        const failure = error instanceof ApiError ? error : new ApiError(0, '无法连接登录服务', 'network')
        const transient = failure.kind !== 'aborted' && (failure.status === 0 || failure.status === 429 || failure.status >= 500)
        const scheduled = transient && attempt < RETRY_DELAYS_MS.length
        const phase = failure.kind === 'timeout' || (failure.kind !== 'protocol' && [502, 503, 504].includes(failure.status))
          ? 'waking' : failure.status === 0 ? 'unreachable' : 'unavailable'
        setState({ phase, checking: false, scheduled, attempt: attempt + 1 })
        if (scheduled) timer = window.setTimeout(() => { void probe(attempt + 1) }, RETRY_DELAYS_MS[attempt])
      } finally { if (active) checking.current = false }
    }
    void probe(0)
    const dispose = () => { active = false; window.clearTimeout(timer); controller?.abort() }
    cancel.current = dispose
    return dispose
  }, [cycle])
  const retry = useCallback(() => {
    // Coalesce double clicks before React has painted the disabled button.
    if (checking.current) return
    cancel.current()
    checking.current = true
    setCycle(value => value + 1)
  }, [])
  return { ...state, retry }
}
