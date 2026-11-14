import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from '@/api/client'
import { Card } from '@/ui/Card'
import { Skeleton } from '@/ui/Skeleton'

const SHOWN = ['ANNUAL', 'SICK', 'COMP_OFF', 'PARENTAL']
const BAR: Record<string, string> = { ANNUAL: 'var(--link)', SICK: 'var(--success)', COMP_OFF: 'var(--warning)', PARENTAL: 'var(--primary-soft-text)' }

export function BalanceCards() {
  const q = useQuery({ queryKey: ['leave', 'balances'], queryFn: () => unwrap(api.GET('/api/leave/balances')) })
  if (q.isError) return <Card as="div"><p className="note note--danger" role="alert">Could not load your balances. {q.error.message}</p></Card>
  const rows = (q.data ?? []).filter((b) => SHOWN.includes(b.type!))
  return (
    <div className="grid leave-balances">
      {q.isPending ? SHOWN.map((s) => <Card as="div" key={s}><Skeleton width={80} height={12} /><div style={{ height: 14 }} /><Skeleton width={120} height={32} /></Card>)
        : rows.map((b) => {
          const pct = b.entitled ? Math.max(0, Math.min(100, ((b.left ?? 0) / b.entitled) * 100)) : 0
          return (
            <Card as="div" key={b.type} aria-label={`${b.name} balance`}>
              <div className="label-caps">{b.type === 'COMP_OFF' ? 'Comp-off' : b.name}</div>
              <div className="balance-value">
                <span className="display balance-value__n">{b.left}</span> <span className="muted">of {b.entitled} days</span>
              </div>
              <div className="bar" role="progressbar" aria-label={`${b.name} days left`} aria-valuemin={0} aria-valuemax={b.entitled} aria-valuenow={b.left}>
                <span style={{ width: `${pct}%`, background: BAR[b.type!] }} />
              </div>
            </Card>
          )
        })}
    </div>
  )
}
