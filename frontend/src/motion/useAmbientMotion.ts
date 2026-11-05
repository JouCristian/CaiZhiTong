import { useEffect, useState } from 'react'

/** Decorative canvas work runs only on visible, motion-enabled desktop screens. */
export function useAmbientMotion() {
  const [enabled, setEnabled] = useState(false)
  useEffect(() => {
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    const desktop = window.matchMedia('(min-width: 960px)')
    const update = () => setEnabled(!motion.matches && desktop.matches && !document.hidden)
    update()
    motion.addEventListener('change', update); desktop.addEventListener('change', update)
    document.addEventListener('visibilitychange', update)
    return () => { motion.removeEventListener('change', update); desktop.removeEventListener('change', update); document.removeEventListener('visibilitychange', update) }
  }, [])
  return enabled
}
