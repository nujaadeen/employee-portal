import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Users } from 'lucide-react'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import type { LeaveRequest } from '@/api/types'
import { usePermission } from '@/auth/me'
import { dateRange } from '@/lib/format'
import { Avatar } from '@/ui/Avatar'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { EmptyState } from '@/ui/EmptyState'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { TextArea } from '@/ui/Field'
import type { Tone } from '@/ui/StatusPill'

export const LEAVE_TONE: Record<string, { label: string; tone: Tone }> = {
  PENDING: { label: 'Pending', tone: 'warning' }, APPROVED: { label: 'Approved', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'danger' }, CANCELLED: { label: 'Cancelled', tone: 'neutral' },
}

export function Approvals() {
  const qc = useQueryClient()
  const everyone = usePermission('employee:records')
  const [target, setTarget] = useState<{ req: LeaveRequest; action: 'approve' | 'reject' } | null>(null)
  const [note, setNote] = useState('')
  const q = useQuery({ queryKey: ['leave', 'approvals'], queryFn: () => unwrap(api.GET('/api/leave/requests', { params: { query: { scope: 'approvals', status: 'ALL' } } })) })
  const decide = useMutation({
    mutationFn: ({ req, action }: { req: LeaveRequest; action: 'approve' | 'reject' }) =>
      unwrap(action === 'approve'
        ? api.POST('/api/leave/requests/{id}/approve', { params: { path: { id: req.id } }, body: { note: note || undefined } })
        : api.POST('/api/leave/requests/{id}/reject', { params: { path: { id: req.id } }, body: { note: note || undefined } })),
    onSuccess: () => { setTarget(null); setNote(''); void qc.invalidateQueries({ queryKey: ['leave'] }); void qc.invalidateQueries({ queryKey: ['dashboard'] }); void qc.invalidateQueries({ queryKey: ['me'] }) },
  })
  const close = () => { setTarget(null); setNote(''); decide.reset() }
  // Pending first (soonest first), then the five most recent decisions, like the screen.
  const all = q.data ?? []
  const rows = [
    ...all.filter((r) => r.status === 'PENDING').sort((a, b) => a.from.localeCompare(b.from)),
    ...all.filter((r) => r.status !== 'PENDING').sort((a, b) => b.from.localeCompare(a.from)).slice(0, 5),
  ]
  return (
    <Card title="Requests to approve" id="approvals" action={<StatusPill tone="info"><Users size={14} aria-hidden="true" /> {everyone ? 'Everyone' : 'Your direct reports'}</StatusPill>}>
      {q.isPending ? <SkeletonRows rows={3} label="Loading requests" /> : q.isError ? (
        <p className="note note--danger" role="alert">Could not load requests. {q.error.message} <Button variant="ghost" onClick={() => { void q.refetch() }}>Try again</Button></p>
      ) : rows.length === 0 ? <EmptyState icon={<Users size={24} />} title="Nothing to approve">Requests from your team will show up here.</EmptyState> : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Employee</th><th>Type</th><th>Dates</th><th>Days</th><th>Status</th><th><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td><span className="person"><Avatar name={r.employeeName} size="sm" /><span className="person__name">{r.employeeName}</span></span></td>
                  <td>{r.typeName}</td><td>{dateRange(r.from, r.to)}</td><td>{r.days}</td>
                  <td><StatusPill tone={LEAVE_TONE[r.status]!.tone}>{LEAVE_TONE[r.status]!.label}</StatusPill></td>
                  <td>
                    {r.status === 'PENDING' ? (
                      <span className="row" style={{ justifyContent: 'flex-end' }}>
                        <Button variant="success" aria-label={`Approve ${r.employeeName}`} onClick={() => setTarget({ req: r, action: 'approve' })}>Approve</Button>
                        <Button variant="danger" aria-label={`Reject ${r.employeeName}`} onClick={() => setTarget({ req: r, action: 'reject' })}>Reject</Button>
                      </span>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ConfirmDialog open={!!target} onCancel={close} busy={decide.isPending} destructive={target?.action === 'reject'}
        title={target?.action === 'approve' ? 'Approve this request?' : 'Reject this request?'}
        confirmLabel={target?.action === 'approve' ? 'Approve' : 'Reject'}
        message={target ? `${target.req.employeeName}, ${target.req.typeName?.toLowerCase()} leave, ${dateRange(target.req.from, target.req.to)} (${target.req.days} working days). They will be notified.` : ''}
        onConfirm={() => { if (target) decide.mutate(target) }}>
        <TextArea label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500}
          error={decide.error instanceof ApiError ? decide.error.message : null} />
      </ConfirmDialog>
    </Card>
  )
}
