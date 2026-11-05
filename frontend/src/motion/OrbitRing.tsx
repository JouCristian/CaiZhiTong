import { lazy, Suspense, useId } from 'react'
import { useAmbientMotion } from './useAmbientMotion'

const LoginOrbit = lazy(() => import('./LoginOrbit'))
function StaticRing() {
  const id = useId()
  return <svg viewBox="0 0 86 86" width="86" height="86" aria-hidden="true"><defs><path id={id} d="M43 8a35 35 0 1 1-.01 0" /></defs><text fill="#175dad" fontSize="9" fontWeight="550"><textPath href={'#' + id} textLength="210" lengthAdjust="spacing">GITHUB · 安全登录 · </textPath></text></svg>
}
export function OrbitRing() {
  const animate = useAmbientMotion()
  return <span className="login-orbit-ring" aria-hidden="true">{animate ? <Suspense fallback={<StaticRing />}><LoginOrbit /></Suspense> : <StaticRing />}</span>
}
