import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { Button } from '@/ui/Button'
import { Field, TextArea } from '@/ui/Field'
import { Modal } from '@/ui/Modal'
import { useToast } from '@/ui/Toast'

/** The server's own validation messages are shown next to the field they belong to. */
export function NewRoleModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (name: string) => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/access/roles', { body: { name, description: description || undefined } })),
    onSuccess: (r) => {
      toast.success(`Role ${r.name} created with no permissions yet`)
      void qc.invalidateQueries({ queryKey: ['access', 'roles'] })
      setName(''); setDescription(''); onCreated(r.name); onClose()
    },
    onError: (e) => { if (!(e instanceof ApiError) || !['invalid-role-name', 'role-exists', 'validation'].includes(e.slug)) toast.error(e instanceof ApiError ? e.message : 'Could not create the role.') },
  })
  const err = m.error instanceof ApiError ? m.error : null
  const nameError = err && ['invalid-role-name', 'role-exists', 'validation'].includes(err.slug) ? err.message : null
  return (
    <Modal open={open} onClose={() => { m.reset(); onClose() }} title="New role" description="Creates a role in Keycloak with an empty permission set. Add permissions afterwards."
      actions={<><Button onClick={() => { m.reset(); onClose() }}>Cancel</Button><Button variant="primary" loading={m.isPending} onClick={() => m.mutate()}>Create role</Button></>}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); m.mutate() }}>
        <Field label="Name" value={name} onChange={(e) => setName(e.target.value.toUpperCase().replace(/ /g, '_'))} maxLength={40} autoComplete="off"
          hint="3 to 30 characters: capital letters and underscores, for example CONTRACTOR" error={nameError} />
        <TextArea label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={200} />
      </form>
    </Modal>
  )
}
