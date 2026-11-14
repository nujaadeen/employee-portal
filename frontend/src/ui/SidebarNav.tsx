import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'

export type NavItem = { to: string; label: string; icon: ReactNode; end?: boolean }
export type NavGroup = { label: string; items: NavItem[] }

export function SidebarNav({ groups, onNavigate }: { groups: NavGroup[]; onNavigate?: () => void }) {
  return (
    <nav aria-label="Main">
      {groups.filter((g) => g.items.length > 0).map((g) => (
        <div key={g.label} role="group" aria-label={g.label}>
          <div className="nav-group-label label-caps" aria-hidden="true">{g.label}</div>
          {g.items.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.end} className="nav-item" onClick={onNavigate}>
              {i.icon}
              {i.label}
            </NavLink>
          ))}
        </div>
      ))}
    </nav>
  )
}
