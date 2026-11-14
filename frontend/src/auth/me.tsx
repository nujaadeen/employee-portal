import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { api, unwrap } from '@/api/client'
import type { Me } from '@/api/types'

export const meKey = ['me'] as const

export function useMe() {
  return useQuery({ queryKey: meKey, queryFn: () => unwrap(api.GET('/api/me')), staleTime: 30_000 })
}

export function permissionsOf(me: Me | undefined): ReadonlySet<string> {
  return new Set(me?.permissions ?? [])
}

/** True when the signed-in person holds the permission. The backend still checks every call. */
export function usePermission(permission: string): boolean {
  const { data } = useMe()
  return !!data?.permissions?.includes(permission)
}

export function usePermissions() {
  const { data } = useMe()
  const set = permissionsOf(data)
  return { has: (p: string) => set.has(p), me: data }
}

/** Renders children only when the person has the permission (or any of them, when given a list). */
export function Can({ permission, children, fallback = null }: { permission: string | string[]; children: ReactNode; fallback?: ReactNode }) {
  const { has } = usePermissions()
  const list = Array.isArray(permission) ? permission : [permission]
  return <>{list.some(has) ? children : fallback}</>
}

const ROLE_ORDER = ['ADMIN', 'HR', 'MANAGER', 'SALES', 'ENGINEER', 'EMPLOYEE']

/** The role shown in the sidebar footer: the most senior of the person's roles, in sentence case. */
export function primaryRoleLabel(roles: readonly string[] | undefined): string {
  const r = ROLE_ORDER.find((x) => roles?.includes(x)) ?? 'EMPLOYEE'
  return r === 'HR' ? 'HR' : r.charAt(0) + r.slice(1).toLowerCase()
}
