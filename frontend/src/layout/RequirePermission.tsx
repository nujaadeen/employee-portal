import type { ReactNode } from 'react'
import { usePermissions } from '@/auth/me'
import { Forbidden } from '@/pages/StatusPages'

/** Route guard. The backend still answers 403 on its own; this only avoids showing a dead page. */
export function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { has } = usePermissions()
  return has(permission) ? <>{children}</> : <Forbidden />
}

/** Like RequirePermission, but any one of the permissions is enough. */
export function RequireAnyPermission({ permissions, children }: { permissions: string[]; children: ReactNode }) {
  const { has } = usePermissions()
  return permissions.some(has) ? <>{children}</> : <Forbidden />
}
