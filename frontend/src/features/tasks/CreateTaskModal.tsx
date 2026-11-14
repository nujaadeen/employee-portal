import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { Button } from '@/ui/Button'
import { Field, TextArea } from '@/ui/Field'
import { Modal } from '@/ui/Modal'
import { Select } from '@/ui/Select'

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((p) => ({ value: p, label: p.charAt(0) + p.slice(1).toLowerCase() }))

export function CreateTaskModal({ open, onClose, projectKey }: { open: boolean; onClose: () => void; projectKey: string }) {
  const qc = useQueryClient()
  const [f, setF] = useState({ title: '', description: '', priority: 'MEDIUM', label: '', dueDate: '', assigneeId: '' })
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })
  // There is no "project members" endpoint yet, so the directory is the candidate list. The server checks membership.
  const people = useQuery({ queryKey: ['employees', 'directory', 'assignees'], enabled: open, queryFn: () => unwrap(api.GET('/api/employees', { params: { query: { scope: 'directory', size: 100 } } })) })
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/tasks', { body: { projectKey, title: f.title, description: f.description || undefined, priority: f.priority as 'MEDIUM', label: f.label || undefined, dueDate: f.dueDate || undefined, assigneeId: f.assigneeId || undefined } })),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['board'] }); void qc.invalidateQueries({ queryKey: ['dashboard'] }); setF({ title: '', description: '', priority: 'MEDIUM', label: '', dueDate: '', assigneeId: '' }); onClose() },
  })
  const err = m.error instanceof ApiError ? m.error : null
  return (
    <Modal open={open} onClose={onClose} title="Create task" description={`New task in ${projectKey}. It goes to the Backlog of the active sprint.`}
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} onClick={() => m.mutate()}>Create task</Button></>}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); m.mutate() }}>
        <Field label="Title" value={f.title} onChange={set('title')} required error={err?.slug === 'validation' ? err.message : null} />
        <TextArea label="Description" value={f.description} onChange={set('description')} />
        <div className="row" style={{ alignItems: 'flex-start' }}>
          <Select className="grow" label="Priority" value={f.priority} onChange={set('priority')} options={PRIORITIES} />
          <Field className="grow" label="Label" value={f.label} onChange={set('label')} placeholder="Backend" />
        </div>
        <div className="row" style={{ alignItems: 'flex-start' }}>
          <Field className="grow" label="Due date" type="date" value={f.dueDate} onChange={set('dueDate')} />
          <Select className="grow" label="Assignee" value={f.assigneeId} onChange={set('assigneeId')} placeholder="Unassigned"
            options={(people.data?.items ?? []).map((p) => ({ value: p.id!, label: p.name! }))} error={err?.slug === 'not-a-member' ? err.message : null} />
        </div>
        {err && !['validation', 'not-a-member'].includes(err.slug) ? <p className="field__error" role="alert">{err.message}</p> : null}
      </form>
    </Modal>
  )
}
