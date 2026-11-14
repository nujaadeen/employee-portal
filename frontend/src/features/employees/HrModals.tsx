import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import type { EmployeeItem } from '@/api/types'
import { usePermission } from '@/auth/me'
import { Button } from '@/ui/Button'
import { Field } from '@/ui/Field'
import { Modal } from '@/ui/Modal'
import { Select } from '@/ui/Select'
import { useToast } from '@/ui/Toast'
import { roleLabel } from './StatusLabel'

/** HR may hand out these four. HR and ADMIN need access:manage (the server enforces it too). */
const HR_ROLES = ['EMPLOYEE', 'SALES', 'ENGINEER', 'MANAGER']

function useRoleOptions() {
  const admin = usePermission('access:manage')
  return (admin ? [...HR_ROLES, 'HR', 'ADMIN'] : HR_ROLES).map((r) => ({ value: r, label: roleLabel(r) }))
}

function useManagerOptions() {
  const q = useQuery({ queryKey: ['employees', 'directory', 'managers'], queryFn: () => unwrap(api.GET('/api/employees', { params: { query: { scope: 'directory', size: 100 } } })) })
  return (q.data?.items ?? []).map((e) => ({ value: e.id, label: `${e.name}${e.jobTitle ? ` · ${e.jobTitle}` : ''}` }))
}

function errText(e: unknown) { return e instanceof ApiError ? e.message : e ? 'Something went wrong.' : null }

function useRefresh() {
  const qc = useQueryClient()
  return () => { void qc.invalidateQueries({ queryKey: ['employees'] }); void qc.invalidateQueries({ queryKey: ['employee'] }) }
}

export function InviteModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const roles = useRoleOptions(); const managers = useManagerOptions(); const refresh = useRefresh(); const toast = useToast()
  const [f, setF] = useState({ email: '', firstName: '', lastName: '', jobTitle: '', department: '', managerId: '', role: 'EMPLOYEE' })
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/employees/invite', { body: { ...f, managerId: f.managerId || undefined } })),
    onSuccess: () => { toast.success(`Invitation sent to ${f.email}`); refresh(); onClose() },
  })
  return (
    <Modal open={open} onClose={onClose} title="Invite employee" description="They get an email to verify their address and choose a password."
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} onClick={() => m.mutate()}>Send invite</Button></>}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); m.mutate() }}>
        <Field label="Work email" type="email" value={f.email} onChange={set('email')} required />
        <div className="row" style={{ alignItems: 'flex-start' }}><Field className="grow" label="First name" value={f.firstName} onChange={set('firstName')} required /><Field className="grow" label="Last name" value={f.lastName} onChange={set('lastName')} required /></div>
        <div className="row" style={{ alignItems: 'flex-start' }}><Field className="grow" label="Job title" value={f.jobTitle} onChange={set('jobTitle')} /><Field className="grow" label="Department" value={f.department} onChange={set('department')} /></div>
        <Select label="Manager" value={f.managerId} onChange={set('managerId')} options={managers} placeholder="No manager yet" />
        <Select label="Role" value={f.role} onChange={set('role')} options={roles} />
        {errText(m.error) ? <p className="field__error" role="alert">{errText(m.error)}</p> : null}
      </form>
    </Modal>
  )
}

export function ApproveModal({ person, onClose }: { person: EmployeeItem | null; onClose: () => void }) {
  const roles = useRoleOptions(); const managers = useManagerOptions(); const refresh = useRefresh(); const toast = useToast()
  const [role, setRole] = useState('EMPLOYEE'); const [managerId, setManagerId] = useState('')
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/employees/{id}/approve', { params: { path: { id: person!.id } }, body: { role, managerId: managerId || undefined } })),
    onSuccess: () => { toast.success(`${person?.name ?? 'Person'} approved`); refresh(); onClose() },
  })
  return (
    <Modal open={!!person} onClose={onClose} title={`Approve ${person?.name ?? ''}`} description="Choose the role and the manager. The person is set to Active."
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} onClick={() => m.mutate()}>Approve</Button></>}>
      <div className="stack">
        <Select label="Role" value={role} onChange={(e) => setRole(e.target.value)} options={roles} />
        <Select label="Manager" value={managerId} onChange={(e) => setManagerId(e.target.value)} options={managers} placeholder="No manager yet" />
        {errText(m.error) ? <p className="field__error" role="alert">{errText(m.error)}</p> : null}
      </div>
    </Modal>
  )
}

export function EditModal({ person, onClose }: { person: EmployeeItem | null; onClose: () => void }) {
  const managers = useManagerOptions(); const refresh = useRefresh(); const toast = useToast()
  const [f, setF] = useState({ jobTitle: person?.jobTitle ?? '', department: person?.department ?? '', location: person?.location ?? '', phone: person?.phone ?? '', giteaUsername: person?.giteaUsername ?? '', managerId: person?.managerId ?? '', status: person?.status ?? 'ACTIVE' })
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })
  const m = useMutation({
    mutationFn: () => unwrap(api.PATCH('/api/employees/{id}', { params: { path: { id: person!.id } }, body: { ...f, managerId: f.managerId || undefined, status: f.status as 'ACTIVE' } })),
    onSuccess: () => { toast.success('Record saved'); refresh(); onClose() },
  })
  if (!person) return null
  return (
    <Modal open onClose={onClose} title={`Edit ${person.name}`}
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} onClick={() => m.mutate()}>Save</Button></>}>
      <div className="stack">
        <Field label="Job title" value={f.jobTitle} onChange={set('jobTitle')} />
        <div className="row" style={{ alignItems: 'flex-start' }}><Field className="grow" label="Department" value={f.department} onChange={set('department')} /><Field className="grow" label="Location" value={f.location} onChange={set('location')} /></div>
        <div className="row" style={{ alignItems: 'flex-start' }}><Field className="grow" label="Phone" value={f.phone} onChange={set('phone')} /><Field className="grow" label="Gitea username" value={f.giteaUsername} onChange={set('giteaUsername')} /></div>
        <Select label="Manager" value={f.managerId} onChange={set('managerId')} options={managers} placeholder="No manager" />
        <Select label="Status" value={f.status} onChange={set('status')} options={['ACTIVE', 'ON_LEAVE', 'ONBOARDING', 'DEACTIVATED'].map((s) => ({ value: s, label: s.replace('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) }))} />
        {errText(m.error) ? <p className="field__error" role="alert">{errText(m.error)}</p> : null}
      </div>
    </Modal>
  )
}
