import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send } from 'lucide-react'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { useMe } from '@/auth/me'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { Field } from '@/ui/Field'
import { Select } from '@/ui/Select'
import { PreviewSummary } from './PreviewSummary'

export const LEAVE_TYPES = [
  { value: 'ANNUAL', label: 'Annual leave' }, { value: 'SICK', label: 'Sick leave' }, { value: 'COMP_OFF', label: 'Comp-off' },
  { value: 'PARENTAL', label: 'Parental leave' }, { value: 'UNPAID', label: 'Unpaid leave' },
]

/** Server problem slugs mapped to the field they belong to. */
export function fieldFor(slug: string): 'type' | 'from' | 'to' | 'form' {
  switch (slug) {
    case 'unknown-leave-type': case 'insufficient-balance': return 'type'
    case 'leave-overlap': return 'from'
    case 'invalid-dates': case 'no-working-days': return 'to'
    default: return 'form'
  }
}

export function RequestForm({ todayIso }: { todayIso: string }) {
  const { data: me } = useMe()
  const qc = useQueryClient()
  const [type, setType] = useState('ANNUAL')
  const [from, setFrom] = useState(todayIso)
  const [to, setTo] = useState(todayIso)
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<Partial<Record<'type' | 'from' | 'to' | 'form', string>>>({})
  const [done, setDone] = useState(false)
  const valid = !!from && !!to && from <= to
  const preview = useQuery({
    queryKey: ['leave', 'preview', type, from, to],
    queryFn: () => unwrap(api.GET('/api/leave/preview', { params: { query: { type, from, to } } })),
    enabled: valid,
  })
  const submit = useMutation({
    mutationFn: () => unwrap(api.POST('/api/leave/requests', { body: { type, from, to, reason: reason || undefined } })),
    onSuccess: () => {
      setErrors({}); setDone(true); setReason('')
      void qc.invalidateQueries({ queryKey: ['leave'] }); void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (e) => {
      setDone(false)
      if (e instanceof ApiError) setErrors({ [fieldFor(e.slug)]: e.message })
      else setErrors({ form: 'Could not submit the request.' })
    },
  })
  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setDone(false)
    if (!valid) { setErrors({ to: 'The end date is before the start date.' }); return }
    setErrors({})
    submit.mutate()
  }
  const preErr = preview.error instanceof ApiError ? preview.error.message : null
  return (
    <Card title="Request leave" id="request-leave" className="leave-form"
      subtitle={me?.manager ? `Your manager, ${me.manager.name}, will be asked to approve.` : 'You have no manager set. HR will be asked to approve.'}>
      <form className="stack" onSubmit={onSubmit} noValidate>
        <Select label="Leave type" value={type} onChange={(e) => { setType(e.target.value); setDone(false) }} options={LEAVE_TYPES} error={errors.type} />
        <div className="row" style={{ alignItems: 'flex-start' }}>
          <Field className="grow" label="From" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setDone(false) }} error={errors.from} />
          <Field className="grow" label="To" type="date" value={to} min={from} onChange={(e) => { setTo(e.target.value); setDone(false) }} error={errors.to ?? (from && to && to < from ? 'The end date is before the start date.' : null)} />
        </div>
        <Field label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
        <PreviewSummary preview={valid ? preview.data : undefined} department={me?.department} />
        {preErr && !errors.to ? <p className="field__error" role="alert">{preErr}</p> : null}
        {errors.form ? <p className="field__error" role="alert">{errors.form}</p> : null}
        {done ? <div className="banner banner--success" role="status">Request submitted. Your manager has been notified.</div> : null}
        <Button type="submit" variant="primary" tall block icon={<Send size={18} />} loading={submit.isPending}>Submit request</Button>
      </form>
    </Card>
  )
}
