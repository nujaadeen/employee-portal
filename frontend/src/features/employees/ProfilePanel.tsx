import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { KeyRound, Mail, Pencil, SquareCheck } from 'lucide-react'
import { ApiError } from '@/api/client'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { useToast } from '@/ui/Toast'
import { api, unwrap } from '@/api/client'
import { Can, usePermission } from '@/auth/me'
import { dateRange, fullDate, plural } from '@/lib/format'
import { LEAVE_TONE } from '@/features/leave/Approvals'
import { Avatar } from '@/ui/Avatar'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { SummaryList } from '@/ui/SummaryList'
import { FacebookIcon, GithubIcon, GoogleIcon } from '@/ui/BrandIcons'
import { MfaBadge } from './MfaBadge'
import { roleLabel } from './StatusLabel'

const ICON: Record<string, React.ReactNode> = { google: <GoogleIcon />, github: <GithubIcon />, facebook: <FacebookIcon />, password: <Mail size={20} /> }

export function ProfilePanel({ id, myId, onEdit }: { id: string; myId?: string; onEdit: () => void }) {
  const q = useQuery({ queryKey: ['employee', id], queryFn: () => unwrap(api.GET('/api/employees/{id}', { params: { path: { id } } })) })
  const hr = usePermission('employee:records')
  const toast = useToast()
  const [resetting, setResetting] = useState(false)
  const reset = useMutation({
    mutationFn: () => unwrap(api.POST('/api/employees/{id}/reset-access', { params: { path: { id } } })),
    onSuccess: () => { setResetting(false); toast.success('Reset email sent') },
    onError: (e) => { setResetting(false); toast.error(e instanceof ApiError ? e.message : 'Could not send the reset email.') },
  })
  const canApprove = usePermission('leave:approve')
  if (q.isPending) return <Card as="aside" aria-label="Profile"><SkeletonRows rows={6} label="Loading profile" /></Card>
  if (q.isError) return <Card as="aside" aria-label="Profile"><p className="note note--danger" role="alert">Could not load this profile. {q.error.message}</p></Card>
  const e = q.data.employee
  const leave = q.data.recentLeave ?? []
  const pending = leave.some((l) => l.status === 'PENDING')
  return (
    <Card as="aside" aria-label={`Profile of ${e.name}`} className="profile-panel">
      <div className="row" style={{ marginBottom: 'var(--space-4)' }}>
        <Avatar name={e.name} size="lg" />
        <div style={{ minWidth: 0 }}>
          <h2 className="card__title">{e.name}</h2>
          <div className="muted">{e.jobTitle}{e.location ? ` · ${e.location}` : ''}</div>
        </div>
      </div>
      <SummaryList label="Employee record" rows={[
        { label: 'Employee ID', value: <span className="mono">{e.code}</span> },
        { label: 'Joined', value: e.joinedOn ? fullDate(e.joinedOn) : '–' },
        { label: e.managerId === myId ? 'Reports to' : 'Manager', value: e.managerId === myId ? 'You' : (e.managerName ?? '–') },
        { label: 'Role', value: roleLabel(e.role) },
        { label: 'Annual leave left', value: e.annualLeaveLeft == null ? '–' : plural(e.annualLeaveLeft, 'day') },
      ]} />
      {hr && q.data.signInMethods ? (
        <>
          <h3 className="label-caps" style={{ margin: 'var(--space-5) 0 var(--space-3)' }}>Sign-in methods</h3>
          <ul className="plain-list">
            {q.data.signInMethods.map((m) => (
              <li key={m.key} className="row signin">
                <span className="signin__icon" aria-hidden="true">{ICON[m.key ?? ''] ?? <KeyRound size={20} />}</span>
                <span className="grow">{m.label}</span>
                <span className="signin__states">
                  <StatusPill tone={m.linked ? 'success' : 'neutral'}>{m.linked ? 'Linked' : 'Not linked'}</StatusPill>
                  <MfaBadge mfa={m.mfa} />
                </span>
              </li>
            ))}
          </ul>
        </>
      ) : null}
      {!hr && leave.length ? (
        <>
          <h3 className="label-caps" style={{ margin: 'var(--space-5) 0 var(--space-3)' }}>Leave</h3>
          <ul className="plain-list">
            {leave.map((l) => (
              <li key={l.id} className="row" style={{ padding: 'var(--space-3) 0', borderTop: '1px solid var(--border-subtle)' }}>
                <div className="grow"><strong>{l.typeName} · {dateRange(l.from, l.to)}</strong><div className="muted">{plural(l.days ?? 0, 'working day')}</div></div>
                <StatusPill tone={LEAVE_TONE[l.status]!.tone}>{LEAVE_TONE[l.status]!.label}</StatusPill>
              </li>
            ))}
          </ul>
        </>
      ) : null}
      <div className="row" style={{ marginTop: 'var(--space-5)', flexWrap: 'wrap' }}>
        <Can permission="employee:manage">
          <Button variant="primary" icon={<Pencil size={18} />} onClick={onEdit}>Edit record</Button>
          <Button icon={<KeyRound size={18} />} onClick={() => setResetting(true)}>Reset access</Button>
        </Can>
        {!hr && canApprove && pending ? <Button variant="primary" to="/leave#approvals">Review request</Button> : null}
        {!hr && e.managerId === myId ? <Button to="/tasks" icon={<SquareCheck size={18} />}>View tasks</Button> : null}
      </div>
      <ConfirmDialog open={resetting} title={`Reset access for ${e.name}?`} confirmLabel="Send email" busy={reset.isPending}
        message={`An email will be sent to ${e.email} with a link to choose a new password and verify the address.`}
        onCancel={() => setResetting(false)} onConfirm={() => reset.mutate()} />
    </Card>
  )
}
