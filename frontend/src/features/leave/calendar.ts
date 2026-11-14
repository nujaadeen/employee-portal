import { parseDate, toIso } from '@/lib/format'

export type Cell = { iso: string; day: number; inMonth: boolean }

/** Weeks (Monday first) covering a month. Cells outside the month are marked inMonth=false. */
export function monthGrid(year: number, month: number): Cell[][] {
  const first = new Date(year, month, 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - offset)
  const weeks: Cell[][] = []
  for (let w = 0; w < 6; w++) {
    const row: Cell[] = []
    for (let d = 0; d < 7; d++) {
      const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + w * 7 + d)
      row.push({ iso: toIso(date), day: date.getDate(), inMonth: date.getMonth() === month })
    }
    if (w > 0 && !row.some((c) => c.inMonth)) break
    weeks.push(row)
  }
  return weeks
}

export function weekOf(iso: string): Cell[] {
  const d = parseDate(iso)
  const monday = new Date(d.getFullYear(), d.getMonth(), d.getDate() - ((d.getDay() + 6) % 7))
  return Array.from({ length: 7 }, (_, i) => {
    const x = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + i)
    return { iso: toIso(x), day: x.getDate(), inMonth: true }
  })
}

export const isWeekend = (iso: string) => { const g = parseDate(iso).getDay(); return g === 0 || g === 6 }

type Span = { from: string; to: string }
/** Leave spans that cover the day. Weekends are not shown, like the screen. */
export function entriesForDay<T extends Span>(entries: T[], iso: string): T[] {
  if (isWeekend(iso)) return []
  return entries.filter((e) => e.from <= iso && iso <= e.to)
}

export const monthKey = (year: number, month: number) => `${year}-${String(month + 1).padStart(2, '0')}`
