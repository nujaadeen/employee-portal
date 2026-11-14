import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { UserMinus, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { Avatar } from '@/ui/Avatar'
import { Button } from '@/ui/Button'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { Modal } from '@/ui/Modal'
import { Select } from '@/ui/Select'
import { SkeletonRows } from '@/ui/Skeleton'
import { useToast } from '@/ui/Toast'
import { useMembers } from './useMembers'

type Member = { id: string; name: string; jobTitle?: string | null; initials: string }

const msg = (e: unknown) => (e instanceof ApiError ? e.message : 'Something went wrong.')

/** Project members. Everyone who may open it sees the list; the add and remove actions are offered only when `canManage`. */
export function MembersModal({ open, onClose, projectKey, canManage }: { open: boolean; onClose: () => void; projectKey: string; canManage: boolean }) {
  const qc = useQueryClient()
  const toast = useToast()
  const members = useMembers(projectKey, open)
  const [pick, setPick] = useState('')
  const [removing, setRemoving] = useState<Member | null>(null)
  const people = useQuery({
    queryKey: ['employees', 'directory', 'members-picker'], enabled: open && canManage,
    queryFn: () => unwrap(api.GET('/api/employees', { params: { query: { scope: 'directory', size: 100 } } })),
  })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ['members', projectKey] }); void qc.invalidateQueries({ queryKey: ['projects'] }) }
  const add = useMutation({
    mutationFn: () => unwrap(api.POST('/api/projects/{key}/members', { params: { path: { key: projectKey } }, body: { employeeId: pick } })),
    onSuccess: () => { toast.success('Member added'); setPick(''); refresh() },
    onError: (e) => toast.error(msg(e)),
  })
  const remove = useMutation({
    mutationFn: (m: Member) => unwrap(api.DELETE('/api/projects/{key}/members/{employeeId}', { params: { path: { key: projectKey, employeeId: m.id } } })),
    onSuccess: (_d, m) => { toast.success(`${m.name} removed from ${projectKey}`); setRemoving(null); refresh() },
    onError: (e) => { toast.error(msg(e)); setRemoving(null) },
  })
  const list = members.data ?? []
  const candidates = (people.data?.items ?? []).filter((p) => !list.some((m) => m.id === p.id))
  return (
    <>
      <Modal open={open} onClose={onClose} title={`${projectKey} members`} description="Only members can see the project and be assigned its tasks."
        actions={<Button variant="primary" onClick={onClose}>Done</Button>}>
        {members.isPending ? <SkeletonRows rows={4} label="Loading members" /> : members.isError ? (
          <p className="note note--danger" role="alert">Could not load members. {members.error.message}</p>
        ) : (
          <ul className="plain-list members">
            {list.map((m) => (
              <li key={m.id} className="row members__row">
                <Avatar name={m.name} size="sm" />
                <div className="grow"><strong>{m.name}</strong><div className="muted">{m.jobTitle ?? ''}</div></div>
                {canManage ? <Button icon={<UserMinus size={18} />} aria-label={`Remove ${m.name}`} onClick={() => setRemoving(m)}>Remove</Button> : null}
              </li>
            ))}
          </ul>
        )}
        {canManage ? (
          <form className="row members__add" onSubmit={(e) => { e.preventDefault(); if (pick) add.mutate() }}>
            <Select className="grow" label="Add a member" value={pick} onChange={(e) => setPick(e.target.value)} placeholder="Choose a person"
              options={candidates.map((p) => ({ value: p.id, label: p.name }))} />
            <Button type="submit" icon={<UserPlus size={18} />} loading={add.isPending} disabled={!pick}>Add</Button>
          </form>
        ) : null}
      </Modal>
      <ConfirmDialog open={!!removing} title={`Remove ${removing?.name ?? ''}?`} confirmLabel="Remove" destructive busy={remove.isPending}
        message={`They lose access to ${projectKey} and cannot be assigned its tasks. Tasks already assigned to them stay as they are.`}
        onCancel={() => setRemoving(null)} onConfirm={() => { if (removing) remove.mutate(removing) }} />
    </>
  )
}
