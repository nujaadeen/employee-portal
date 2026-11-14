import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router-dom'

type Variant = 'primary' | 'secondary' | 'danger' | 'success' | 'ghost'
type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant
  icon?: ReactNode
  block?: boolean
  tall?: boolean
  loading?: boolean
  /** Renders a link that looks like a button. */
  to?: string
}

export function Button({ variant = 'secondary', icon, block, tall, loading, to, children, className = '', disabled, type = 'button', ...rest }: Props) {
  const cls = `btn btn--${variant}${block ? ' btn--block' : ''}${tall ? ' btn--tall' : ''} ${className}`.trim()
  const content = (
    <>
      {icon}
      {loading ? 'Working…' : children}
    </>
  )
  if (to) return <Link to={to} className={cls}>{content}</Link>
  return (
    <button type={type} className={cls} disabled={disabled || loading} {...rest}>
      {content}
    </button>
  )
}
