import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { Button } from '@/ui/Button'
import { Field } from '@/ui/Field'
import { Modal } from '@/ui/Modal'
import { useToast } from '@/ui/Toast'
import { validateCode, validateName } from './validation'

export function NewCollectionModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (code: string) => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [touched, setTouched] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const codeError = touched ? validateCode(code) : null
  const nameError = touched ? validateName(name) : null
  const create = useMutation({
    mutationFn: () => unwrap(api.POST('/api/knowledge/collections', { body: { code, name: name.trim() } })),
    onSuccess: (c) => { toast.success(`Collection ${c.name} created. Only administrators can search it until you give roles access.`); void qc.invalidateQueries({ queryKey: ['knowledge'] }); void qc.invalidateQueries({ queryKey: ['ai'] }); close(); onCreated(c.code) },
    onError: (e) => setServerError(e instanceof ApiError ? e.message : 'Could not create the collection.'),
  })
  const close = () => { setCode(''); setName(''); setTouched(false); setServerError(null); onClose() }
  const submit = (e: React.FormEvent) => { e.preventDefault(); setTouched(true); setServerError(null); if (!validateCode(code) && !validateName(name)) create.mutate() }
  return (
    <Modal open={open} title="New collection" onClose={close} description="A collection is a group of documents the assistant can search. You choose which roles may search it."
      actions={<><Button onClick={close}>Cancel</Button><Button type="submit" form="new-collection" variant="primary" loading={create.isPending}>Create collection</Button></>}>
      <form id="new-collection" onSubmit={submit} noValidate>
        <Field label="Name" value={name} maxLength={100} error={nameError} onChange={(e) => setName(e.target.value)} />
        <Field label="Code" value={code} maxLength={40} error={codeError ?? serverError} hint="3 to 40 characters: lowercase letters, digits and hyphens, starting with a letter. It cannot be changed later."
          onChange={(e) => { setCode(e.target.value.toLowerCase()); setServerError(null) }} />
      </form>
    </Modal>
  )
}
