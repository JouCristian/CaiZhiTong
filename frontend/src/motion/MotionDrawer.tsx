import { cloneElement, createContext, isValidElement, useCallback, useContext, useLayoutEffect, useRef, useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { Drawer } from 'antd'
import type { DrawerProps } from 'antd'
import './motion.css'

const ExitContext = createContext({ exiting: false, complete: () => {} })

/** Keep conditional drawer owners alive until the actual exit transition ends. */
export function DrawerPresence({ children }: { children: ReactNode }) {
  const inherited = useContext(ExitContext)
  const [retained, setRetained] = useState(children)
  const present = Boolean(children)
  const presentRef = useRef(present)
  const complete = useCallback(() => { if (!presentRef.current) setRetained(null) }, [])
  useLayoutEffect(() => {
    presentRef.current = present
    if (present) { setRetained(children); return }
    const timeout = window.setTimeout(complete, 460)
    return () => window.clearTimeout(timeout)
  }, [children, present, complete])
  return <ExitContext.Provider value={{ exiting: inherited.exiting || !present, complete }}>{present ? children : retained}</ExitContext.Provider>
}

export function MotionDrawer({ open, rootClassName, rootStyle, afterOpenChange, destroyOnClose, destroyOnHidden, drawerRender, ...props }: DrawerProps) {
  const exit = useContext(ExitContext)
  const shown = Boolean(open) && !exit.exiting
  const [entered, setEntered] = useState(false)
  useLayoutEffect(() => { if (!shown) setEntered(false) }, [shown])
  return <Drawer {...props} open={shown} aria-hidden={shown ? props['aria-hidden'] : true}
    data-motion-ready={shown && entered ? 'true' : 'false'}
    destroyOnHidden={destroyOnHidden ?? destroyOnClose ?? true}
    rootClassName={['czt-drawer', rootClassName].filter(Boolean).join(' ')}
    rootStyle={{ ...rootStyle, ...(!shown ? { pointerEvents: 'none' } : {}) }}
    drawerRender={node => {
      // A departing panel stays visible for motion, but immediately leaves the
      // focus and accessibility trees when its successor opens.
      const panel = isValidElement(node) ? cloneElement(node as ReactElement<{ inert?: boolean }>, { inert: !shown }) : node
      return drawerRender ? drawerRender(panel) : panel
    }}
    afterOpenChange={shown => {
      // rc-drawer restores its original trigger after this callback. Preserve
      // focus if a successor overlay has already taken over (save → details).
      const active = document.activeElement as HTMLElement | null
      if (!shown && active?.closest('.ant-drawer-open, .ant-modal-wrap')) {
        queueMicrotask(() => { if (active.isConnected && !active.closest('[inert]')) active.focus({ preventScroll: true }) })
      }
      setEntered(shown); afterOpenChange?.(shown); if (!shown) exit.complete()
    }} />
}
