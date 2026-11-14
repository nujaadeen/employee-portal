import type { ReactNode } from 'react'
import { NotificationBell } from '@/features/notifications/NotificationBell'

type Props = { title: string; subtitle?: ReactNode; actions?: ReactNode; crumbs?: ReactNode; bell?: boolean }

/** Page title, helper text in --text-secondary, the notification bell and the page's one primary action. */
export function PageHeader({ title, subtitle, actions, crumbs, bell = true }: Props) {
  return (
    <>
      {crumbs ? <div className="crumbs">{crumbs}</div> : null}
      <div className="page-header">
        <div style={{ minWidth: 0 }}>
          <h1>{title}</h1>
          {subtitle ? <p>{subtitle}</p> : null}
        </div>
        <div className="page-header__actions">
          {bell ? <NotificationBell /> : null}
          {actions}
        </div>
      </div>
    </>
  )
}
