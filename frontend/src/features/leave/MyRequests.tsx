import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarX } from 'lucide-react'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import type { LeaveRequest } from '@/api/types'
import { dateRange } from '@/lib/format'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { EmptyState } from '@/ui/EmptyState'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { LEAVE_TONE } from './Approvals'

export function MyRequests() {
  const qc = useQueryClient()
  const [cancelling, setCancelling] = useState<LeaveRequest | null>(null)
  const q = useQuery({ queryKey: ['leave', 'mine'], queryFn: () => unwrap(api.GET('/api/leave/requests', { params: { query: { scope: 'mine' } } })) })
  const cancel = useMutation({
    mutationFn: (r: LeaveRequest) => unwrap(api.DELETE('/api/leave/requests/{id}', { params: { path: { id: r.id } } })),
    onSuccess: () => { setCancelling(null); void qc.invalidateQueries({ queryKey: ['leave'] }) },
  })
  const rows = q.data ?? []
  return (
    <Card title="My requests" subtitle="You can cancel a request while it is pending.">
      {q.isPending ? <SkeletonRows rows={2} label="Loading your requests" /> : q.isError ? <p className="note note--danger" role="alert">Could not load your requests.</p>
        : rows.length === 0 ? <EmptyState icon={<CalendarX size={24} />} title="No requests yet">Use the form to ask for time off.</EmptyState> : (
          <ul className="plain-list">
            {rows.map((r) => (
              <li key={r.id} className="row my-req">
                <div className="grow"><strong>{r.typeName} · {dateRange(r.from, r.to)}</strong><div className="muted">{r.days} working days{r.note ? ` · ${r.note}` : ''}</div></div>
                <StatusPill tone={LEAVE_TONE[r.status]!.tone}>{LEAVE_TONE[r.status]!.label}</StatusPill>
                {r.status === 'PENDING' ? <Button onClick={() => setCancelling(r)}>Cancel</Button> : null}
              </li>
            ))}
          </ul>
        )}
      <ConfirmDialog open={!!cancelling} title="Cancel this request?" message="Your manager will no longer see it." confirmLabel="Cancel request" destructive busy={cancel.isPending}
        onCancel={() => { setCancelling(null); cancel.reset() }} onConfirm={() => { if (cancelling) cancel.mutate(cancelling) }}>
        {cancel.error instanceof ApiError ? <p className="field__error" role="alert">{cancel.error.message}</p> : null}
      </ConfirmDialog>
    </Card>
  )
}
