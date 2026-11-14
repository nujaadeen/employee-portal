const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const MONTHS_LONG = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const DAYS_LONG = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

/** Parses yyyy-MM-dd as a local calendar date (no timezone shifts). */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y!, (m ?? 1) - 1, d ?? 1)
}

export function toIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export const monthName = (m: number) => MONTHS_LONG[m]!

/** "13 Nov" */
export const shortDate = (iso: string) => { const d = parseDate(iso); return `${d.getDate()} ${MONTHS[d.getMonth()]}` }
/** "Fri 13 Nov" */
export const weekdayDate = (iso: string) => { const d = parseDate(iso); return `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}` }
/** "3 Mar 2025" */
export const fullDate = (iso: string) => { const d = parseDate(iso); return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}` }
/** "Friday 13 November 2026" */
export const longDate = (iso: string) => { const d = parseDate(iso); return `${DAYS_LONG[d.getDay()]} ${d.getDate()} ${MONTHS_LONG[d.getMonth()]} ${d.getFullYear()}` }

/** "23 to 27 Nov", "30 Nov", "28 Nov to 2 Dec" */
export function dateRange(from: string, to: string): string {
  if (from === to) return shortDate(from)
  const a = parseDate(from), b = parseDate(to)
  return a.getMonth() === b.getMonth() ? `${a.getDate()} to ${shortDate(to)}` : `${shortDate(from)} to ${shortDate(to)}`
}

export const plural = (n: number, one: string, many = `${one}s`) => `${n} ${n === 1 ? one : many}`

export function greeting(hour: number): string {
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
}

/** "2 days ago", "just now" */
export function timeAgo(iso: string, now = Date.now()): string {
  const mins = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000))
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} min ago`
  if (mins < 1440) return `${Math.round(mins / 60)}h ago`
  const days = Math.round(mins / 1440)
  return days === 1 ? 'yesterday' : `${days} days ago`
}

/** "Today", "Tomorrow", otherwise "Fri 20 Nov" */
export function dueLabel(iso: string, todayIso: string): string {
  if (iso === todayIso) return 'Today'
  return weekdayDate(iso)
}

/** Role name for people: Sales, Manager, but HR stays HR. */
export const roleName = (r?: string) => (!r ? '' : r === 'HR' ? 'HR' : r.charAt(0) + r.slice(1).toLowerCase())

export function titleCase(s: string) { return s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') }

/** "Today, 14:20", "Yesterday, 09:05", otherwise "Tue 10 Nov" */
export function activityTime(iso: string, now = new Date()): string {
  const d = new Date(iso)
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  const days = Math.round((new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime() - new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()) / 86400000)
  if (days === 0) return `Today, ${hm}`
  if (days === 1) return `Yesterday, ${hm}`
  return weekdayDate(toIso(d))
}

/** "9 to 20 November" */
export function longRange(from: string, to: string): string {
  const a = parseDate(from), b = parseDate(to)
  return a.getMonth() === b.getMonth() ? `${a.getDate()} to ${b.getDate()} ${MONTHS_LONG[b.getMonth()]}` : `${a.getDate()} ${MONTHS_LONG[a.getMonth()]} to ${b.getDate()} ${MONTHS_LONG[b.getMonth()]}`
}
