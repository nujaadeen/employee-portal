import { Moon, Sun } from 'lucide-react'
import { useState, useSyncExternalStore } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { ApiError, pendingApproval } from '@/api/client'
import { primaryRoleLabel, usePermissions } from '@/auth/me'
import { useMe } from '@/auth/me'
import { ErrorPage, WaitingForApproval } from '@/pages/StatusPages'
import { AppShell } from '@/ui/AppShell'
import { Avatar } from '@/ui/Avatar'
import { IconButton } from '@/ui/IconButton'
import { SidebarNav } from '@/ui/SidebarNav'
import { getStoredTheme, applyTheme } from '@/theme'
import type { Theme } from '@/theme'
import { buildNav } from './nav'

function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(getStoredTheme())
  return (
    <IconButton bare label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'} onClick={() => { const t = theme === 'dark' ? 'light' : 'dark'; applyTheme(t); setTheme(t) }}>
      {theme === 'dark' ? <Moon size={20} /> : <Sun size={20} />}
    </IconButton>
  )
}

export function Layout() {
  const me = useMe()
  const { has } = usePermissions()
  const pending = useSyncExternalStore(pendingApproval.subscribe, pendingApproval.get)
  if (me.isError) {
    if (me.error instanceof ApiError && me.error.slug === 'pending-approval') return <WaitingForApproval />
    return <ErrorPage message={me.error.message} onRetry={() => { void me.refetch() }} />
  }
  if (me.isPending) return <div className="status-page" role="status"><p className="muted">Loading…</p></div>
  if (me.data.status === 'PENDING_APPROVAL' || pending) return <WaitingForApproval />
  const name = me.data.name ?? ''
  return (
    <AppShell
      nav={(close) => <SidebarNav groups={buildNav(has)} onNavigate={close} />}
      footer={<>
        <Avatar name={name} />
        <Link to="/profile" className="sidebar__user">
          <strong>{name}</strong>
          <span>{primaryRoleLabel(me.data.roles)} · View profile</span>
        </Link>
        <ThemeToggle />
      </>}>
      <Outlet />
    </AppShell>
  )
}
