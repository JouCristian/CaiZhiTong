import { useEffect, useRef, useState } from 'react'
import { Avatar, Popover } from 'antd'
import { DownOutlined, LogoutOutlined } from '@ant-design/icons'
import type { User } from './api'
import { roleNames } from './modules'

export function AccountMenu({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [open, setOpen] = useState(false)
  const [avatarFailed, setAvatarFailed] = useState(false)
  useEffect(() => setAvatarFailed(false), [user.avatarUrl])
  const trigger = useRef<HTMLButtonElement>(null)
  return <Popover trigger="click" placement="bottomRight" arrow={false} open={open} onOpenChange={setOpen}
    styles={{ body: { padding: 6 } }} content={
      <div role="menu" aria-label="账户操作" className="account-popup" onKeyDown={event => {
        if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() }
      }}>
        <div className="account-popup-identity">{user.githubLogin ? '@' + user.githubLogin : user.username}<span>{user.roles.map(role => roleNames[role] || role).join('、')}</span></div>
        <button type="button" role="menuitem" className="account-logout" onClick={() => {
          // Bind the session action directly to the button, without a menu-item event chain.
          onLogout()
          setOpen(false)
        }}><LogoutOutlined aria-hidden />退出登录</button>
      </div>
    }>
    <button ref={trigger} type="button" className="user-trigger" aria-label="账户菜单" aria-haspopup="menu" aria-expanded={open}>
      <Avatar size={31} className="user-avatar" src={user.avatarUrl && !avatarFailed ? <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" onError={() => setAvatarFailed(true)} /> : undefined}>{user.displayName.slice(0, 1)}</Avatar><span>{user.displayName}</span><DownOutlined aria-hidden />
    </button>
  </Popover>
}
