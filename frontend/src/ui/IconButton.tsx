import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  label: string
  children: ReactNode
  badge?: number
  bare?: boolean
}

/** Square 44px icon button. `label` is the accessible name. */
export function IconButton({ label, children, badge, bare, className = '', type = 'button', ...rest }: Props) {
  return (
    <button type={type} aria-label={label} className={`icon-btn${bare ? ' icon-btn--bare' : ''} ${className}`.trim()} {...rest}>
      {children}
      {badge ? <span className="badge-dot" aria-hidden="true">{badge > 99 ? '99+' : badge}</span> : null}
    </button>
  )
}
