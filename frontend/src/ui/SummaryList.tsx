import type { ReactNode } from 'react'

export type SummaryRow = { label: string; value: ReactNode }

export function SummaryList({ rows, label }: { rows: SummaryRow[]; label?: string }) {
  return (
    <dl className="summary" aria-label={label}>
      {rows.map((r) => (
        <div key={r.label}>
          <dt>{r.label}</dt>
          <dd>{r.value}</dd>
        </div>
      ))}
    </dl>
  )
}
