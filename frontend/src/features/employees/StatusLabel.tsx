import { StatusPill } from '@/ui/StatusPill'
import type { Tone } from '@/ui/StatusPill'

const MAP: Record<string, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Active', tone: 'success' },
  ON_LEAVE: { label: 'On leave', tone: 'warning' },
  ONBOARDING: { label: 'Onboarding', tone: 'info' },
  PENDING_APPROVAL: { label: 'Awaiting approval', tone: 'warning' },
  DEACTIVATED: { label: 'Deactivated', tone: 'neutral' },
}

export function EmployeeStatus({ status }: { status?: string }) {
  const m = MAP[status ?? ''] ?? { label: status ?? '', tone: 'neutral' as Tone }
  return <StatusPill tone={m.tone}>{m.label}</StatusPill>
}

export const roleLabel = (r?: string) => (r ? r.charAt(0) + r.slice(1).toLowerCase() : '')
