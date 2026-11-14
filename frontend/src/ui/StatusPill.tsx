import type { ReactNode } from 'react'

export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

/** State is always shown as text, never by colour alone. */
export function StatusPill({ tone = 'neutral', children, mono }: { tone?: Tone; children: ReactNode; mono?: boolean }) {
  return <span className={`pill pill--${tone}${mono ? ' pill--mono' : ''}`}>{children}</span>
}
