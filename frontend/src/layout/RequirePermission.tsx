import type { ReactNode } from 'react'
import { usePermissions } from '@/auth/me'
import { Forbidden } from '@/pages/StatusPages'

/** Route guard. The backend still answers 403 on its own; this only avoids showing a dead page. */
export function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { has } = usePermissions()
  return has(permission) ? <>{children}</> : <Forbidden />
}
