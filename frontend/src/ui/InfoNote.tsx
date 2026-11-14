import { CircleAlert, Info } from 'lucide-react'
import type { ReactNode } from 'react'

export function InfoNote({ children, tone }: { children: ReactNode; tone?: 'success' | 'danger' | 'warning' }) {
  return (
    <p className={`note${tone ? ` note--${tone}` : ''}`}>
      {tone === 'danger' ? <CircleAlert size={16} aria-hidden="true" /> : <Info size={16} aria-hidden="true" />}
      <span>{children}</span>
    </p>
  )
}
