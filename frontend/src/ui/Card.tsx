import type { HTMLAttributes, ReactNode } from 'react'

type Props = Omit<HTMLAttributes<HTMLElement>, 'title'> & { title?: ReactNode; subtitle?: ReactNode; action?: ReactNode; as?: 'section' | 'div' }

export function Card({ title, subtitle, action, as: Tag = 'section', children, className = '', ...rest }: Props) {
  return (
    <Tag className={`card ${className}`.trim()} {...rest}>
      {title || action ? (
        <div className="card__head">
          <div>
            {title ? <h2 className="card__title">{title}</h2> : null}
            {subtitle ? <p className="card__sub">{subtitle}</p> : null}
          </div>
          {action}
        </div>
      ) : null}
      {children}
    </Tag>
  )
}
