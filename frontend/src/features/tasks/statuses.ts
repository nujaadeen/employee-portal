import type { TaskStatus } from '@/api/types'
import type { Tone } from '@/ui/StatusPill'

export const STATUSES: { value: TaskStatus; label: string; tone: Tone }[] = [
  { value: 'BACKLOG', label: 'Backlog', tone: 'neutral' },
  { value: 'IN_PROGRESS', label: 'In progress', tone: 'info' },
  { value: 'IN_REVIEW', label: 'In review', tone: 'warning' },
  { value: 'DONE', label: 'Done', tone: 'success' },
]
export const statusInfo = (s?: string) => STATUSES.find((x) => x.value === s) ?? STATUSES[0]!
export const nextStatus = (s?: string): TaskStatus | null => { const i = STATUSES.findIndex((x) => x.value === s); return STATUSES[i + 1]?.value ?? null }

export const LABEL_TONE: Record<string, Tone> = { Backend: 'info', Frontend: 'warning', Security: 'danger', 'HR module': 'success', Platform: 'neutral' }
