import { Calendar, LayoutDashboard, Shield, Sparkles, SquareCheck, Users } from 'lucide-react'
import type { NavGroup } from '@/ui/SidebarNav'

const I = 20

/**
 * Sidebar by permission. A group with no visible item is hidden.
 * ("Task detail" from the screens is not a menu item: it opens from a task card.)
 */
export function buildNav(has: (permission: string) => boolean): NavGroup[] {
  return [
    { label: 'Workspace', items: [{ to: '/', label: 'Dashboard', icon: <LayoutDashboard size={I} />, end: true }] },
    {
      label: 'People',
      items: [
        ...(has('leave:request') ? [{ to: '/leave', label: 'Leave', icon: <Calendar size={I} /> }] : []),
        ...(has('employee:directory') ? [{ to: '/employees', label: 'Employees', icon: <Users size={I} /> }] : []),
      ],
    },
    { label: 'Work', items: has('task:view') ? [{ to: '/tasks', label: 'Tasks', icon: <SquareCheck size={I} /> }] : [] },
    { label: 'Assistant', items: has('ai:chat') ? [{ to: '/ai', label: 'AI assistant', icon: <Sparkles size={I} /> }] : [] },
    { label: 'Admin', items: has('access:manage') ? [{ to: '/access', label: 'Roles and access', icon: <Shield size={I} /> }] : [] },
  ]
}
