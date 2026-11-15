import type { Me } from '@/api/types'

/** The permission sets of the seeded roles, as the backend sends them in GET /api/me. */
const EMPLOYEE = ['profile:read-own', 'profile:edit-own', 'leave:request', 'employee:directory', 'task:view', 'task:create', 'task:edit', 'ai:chat']
const SALES = ['ai:knowledge:product', 'ai:knowledge:brand', 'ai:knowledge:campaign', 'social:publish:company', 'social:publish:personal']
const MANAGER = ['leave:approve', 'employee:team']
const HR = ['leave:approve', 'employee:team', 'employee:records', 'employee:manage', 'ai:knowledge:hr-policies']

export const PEOPLE = {
  aisha: { name: 'Aisha Rahman', roles: ['EMPLOYEE', 'SALES'], permissions: [...EMPLOYEE, ...SALES] },
  omar: { name: 'Omar Haddad', roles: ['EMPLOYEE', 'MANAGER'], permissions: [...EMPLOYEE, ...MANAGER] },
  lena: { name: 'Lena Fischer', roles: ['EMPLOYEE', 'HR'], permissions: [...EMPLOYEE, ...HR] },
  daniel: { name: 'Daniel Okoye', roles: ['EMPLOYEE', 'ADMIN', 'ENGINEER', 'MANAGER'], permissions: [...EMPLOYEE, ...MANAGER, ...HR, 'repo:view', 'access:manage', 'ai:knowledge:manage', ...SALES] },
} as const

export function meFor(key: keyof typeof PEOPLE): Me {
  const p = PEOPLE[key]
  return { id: `id-${key}`, code: 'EMP-0001', name: p.name, email: `${key}@baracel.example`, status: 'ACTIVE', roles: [...p.roles], permissions: [...p.permissions], signInMethods: [], unreadNotifications: 0, department: 'Sales' }
}

export const has = (key: keyof typeof PEOPLE) => (perm: string) => (PEOPLE[key].permissions as readonly string[]).includes(perm)
