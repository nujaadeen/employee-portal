import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { api, unwrap } from '@/api/client'
import type { CalendarEntry } from '@/api/types'
import { monthName, toIso, weekdayDate } from '@/lib/format'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { IconButton } from '@/ui/IconButton'
import { SegmentedTabs } from '@/ui/SegmentedTabs'
import { SkeletonRows } from '@/ui/Skeleton'
import { entriesForDay, monthGrid, monthKey, weekOf } from './calendar'

const DOW = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const TYPE: Record<string, string> = { ANNUAL: 'Annual', SICK: 'Sick', COMP_OFF: 'Comp-off', PARENTAL: 'Parental', UNPAID: 'Unpaid' }

function Chip({ e }: { e: CalendarEntry }) {
  const first = (e.name ?? '').split(' ')[0]
  const label = `${e.name}, ${TYPE[e.type ?? ''] ?? e.type} leave${e.status === 'PENDING' ? ', pending' : ''}`
  return <span className={`cal-chip cal-chip--${e.status === 'PENDING' ? 'pending' : 'approved'}`} title={label} aria-label={label}>{first} · {TYPE[e.type ?? ''] ?? e.type}</span>
}

export function TeamCalendar({ todayIso }: { todayIso: string }) {
  const now = new Date(todayIso + 'T00:00:00')
  const [view, setView] = useState<'month' | 'week'>('month')
  const [cursor, setCursor] = useState({ y: now.getFullYear(), m: now.getMonth() })
  const [weekStart, setWeekStart] = useState(todayIso)
  const key = monthKey(cursor.y, cursor.m)
  const q = useQuery({ queryKey: ['leave', 'calendar', key], queryFn: () => unwrap(api.GET('/api/leave/calendar', { params: { query: { month: key } } })) })
  const entries = q.data ?? []
  const shift = (n: number) => {
    if (view === 'month') { const d = new Date(cursor.y, cursor.m + n, 1); setCursor({ y: d.getFullYear(), m: d.getMonth() }) }
    else {
      const d = new Date(weekStart + 'T00:00:00'); d.setDate(d.getDate() + 7 * n)
      setWeekStart(toIso(d)); setCursor({ y: d.getFullYear(), m: d.getMonth() })
    }
  }
  const title = `Team calendar · ${monthName(cursor.m)} ${cursor.y}`
  return (
    <Card as="section" aria-label="Team calendar" className="leave-calendar">
      <div className="card__head">
        <h2 className="card__title">{title}</h2>
        <div className="row">
          <IconButton bare label="Previous" onClick={() => shift(-1)}><ChevronLeft size={20} /></IconButton>
          <IconButton bare label="Next" onClick={() => shift(1)}><ChevronRight size={20} /></IconButton>
          <SegmentedTabs label="Calendar view" value={view} onChange={setView} tabs={[{ value: 'month', label: 'Month' }, { value: 'week', label: 'Week' }]} />
        </div>
      </div>
      {q.isError ? <p className="note note--danger" role="alert">Could not load the calendar. <Button variant="ghost" onClick={() => { void q.refetch() }}>Try again</Button></p>
        : q.isPending ? <SkeletonRows rows={5} label="Loading calendar" /> : view === 'month' ? (
          <div className="cal" role="grid" aria-label={title}>
            <div className="cal__row cal__row--head" role="row">{DOW.map((d) => <span key={d} role="columnheader" className="cal__dow">{d}</span>)}</div>
            {monthGrid(cursor.y, cursor.m).map((week, i) => (
              <div className="cal__row" role="row" key={i}>
                {week.map((c) => {
                  const day = c.inMonth ? entriesForDay(entries, c.iso) : []
                  return (
                    <div key={c.iso} role="gridcell" className={`cal__cell${c.inMonth ? '' : ' cal__cell--out'}`} data-today={c.iso === todayIso} aria-label={c.inMonth ? weekdayDate(c.iso) : undefined}>
                      {c.inMonth ? <span className={`cal__day${c.iso === todayIso ? ' cal__day--today' : ''}`}>{c.day}</span> : null}
                      {day.map((e, k) => <Chip key={k} e={e} />)}
                    </div>
                  )
                })}
              </div>
            ))}
          </div>
        ) : (
          <ul className="plain-list cal-week" aria-label="Week view">
            {weekOf(weekStart).map((c) => {
              const day = entriesForDay(entries, c.iso)
              return (
                <li key={c.iso} className="cal-week__row">
                  <span className={`cal-week__day${c.iso === todayIso ? ' cal__day--today' : ''}`}>{weekdayDate(c.iso)}</span>
                  <span className="cal-week__items">{day.length ? day.map((e, k) => <Chip key={k} e={e} />) : <span className="muted">Nobody out</span>}</span>
                </li>
              )
            })}
          </ul>
        )}
    </Card>
  )
}
