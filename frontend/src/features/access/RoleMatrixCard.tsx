import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Minus, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { roleName } from '@/lib/format'
import { Button } from '@/ui/Button'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { useToast } from '@/ui/Toast'
import { Card } from '@/ui/Card'
import { SkeletonRows } from '@/ui/Skeleton'
import { buildMatrix, COLUMNS } from './Matrix'

export function RoleMatrixCard({ role, builtIn, description, onDeleted }: { role: string; builtIn: boolean; description?: string; onDeleted: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const q = useQuery({ queryKey: ['access', 'matrix', role], queryFn: () => unwrap(api.GET('/api/access/roles/{role}/matrix', { params: { path: { role } } })) })
  // Everyone also holds EMPLOYEE, so its permissions show as inherited (ticked, not editable here).
  const base = useQuery({ queryKey: ['access', 'matrix', 'EMPLOYEE'], enabled: role !== 'EMPLOYEE', queryFn: () => unwrap(api.GET('/api/access/roles/{role}/matrix', { params: { path: { role: 'EMPLOYEE' } } })) })
  const inherited = useMemo(() => new Set(role === 'EMPLOYEE' ? [] : (base.data?.permissions ?? []).filter((p) => p.granted).map((p) => p.key)), [base.data, role])
  const server = useMemo(() => new Set((q.data?.permissions ?? []).filter((p) => p.granted).map((p) => p.key)), [q.data])
  const [edits, setEdits] = useState<{ role: string; set: Set<string> } | null>(null)
  const granted = edits && edits.role === role ? edits.set : server
  const dirty = granted.size !== server.size || [...granted].some((k) => !server.has(k))
  const [saved, setSaved] = useState(false)
  const save = useMutation({
    mutationFn: () => unwrap(api.PUT('/api/access/roles/{role}/matrix', { params: { path: { role } }, body: { permissions: [...granted] } })),
    onSuccess: () => { toast.success(`${roleName(role)} permissions saved`); setEdits(null); setSaved(true); void qc.invalidateQueries({ queryKey: ['access'] }); void qc.invalidateQueries({ queryKey: ['me'] }) },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not save the permissions.'),
  })
  const del = useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/access/roles/{role}', { params: { path: { role } } })),
    onSuccess: () => { toast.success(`Role ${role} deleted`); setConfirmDelete(false); void qc.invalidateQueries({ queryKey: ['access', 'roles'] }); onDeleted() },
    onError: (e) => { setConfirmDelete(false); toast.error(e instanceof ApiError ? e.message : 'Could not delete the role.') },
  })
  const toggle = (key: string) => {
    const next = new Set(granted)
    if (next.has(key)) next.delete(key); else next.add(key)
    setSaved(false); setEdits({ role, set: next })
  }
  const groups = useMemo(() => buildMatrix(q.data?.permissions ?? []), [q.data])
  const name = roleName(role)
  return (
    <Card as="section" aria-label={`${name} permissions`} title={name} subtitle={`${q.data?.people ?? '–'} people · Keycloak realm baracel${description ? ` · ${description}` : ''}`}
      action={<span className="row">
        {builtIn ? null : <Button variant="danger" icon={<Trash2 size={18} />} onClick={() => setConfirmDelete(true)}>Delete role</Button>}
        <Button variant="primary" icon={<Check size={18} />} loading={save.isPending} disabled={!dirty} onClick={() => save.mutate()}>Save changes</Button>
      </span>}>
      {q.isPending ? <SkeletonRows rows={6} label="Loading permissions" /> : q.isError ? <p className="note note--danger" role="alert">Could not load this role. {q.error.message}</p> : (
        <div className="table-wrap">
          <table className="table matrix" aria-label={`${name} permission matrix`}>
            <thead><tr><th><span className="sr-only">Permission</span></th>{COLUMNS.map((c) => <th key={c} className="matrix__col">{c}</th>)}</tr></thead>
            {groups.map((g) => (
              <tbody key={g.name}>
                <tr><th colSpan={6} className="matrix__group">{g.name}</th></tr>
                {g.rows.map((r) => (
                  <tr key={r.label}>
                    <th scope="row" className="matrix__row"><strong>{r.label}</strong><div className="muted">{r.hint}</div></th>
                    {COLUMNS.map((c) => {
                      const cell = r.cells[c]
                      if (!cell) return <td key={c} className="matrix__cell"><Minus size={16} aria-label="Not available" className="muted" /></td>
                      const on = granted.has(cell.key)
                      if (!on && inherited.has(cell.key))
                        return <td key={c} className="matrix__cell"><span className="matrix__box matrix__box--inherited" role="img" aria-label={`${r.label}: ${c.toLowerCase()}, from the Employee role`} title="From the Employee role"><Check size={18} style={{ color: 'var(--success)' }} /></span></td>
                      return (
                        <td key={c} className="matrix__cell">
                          <button type="button" role="checkbox" aria-checked={on} aria-label={`${r.label}: ${c.toLowerCase()}`} className="matrix__box" onClick={() => toggle(cell.key)}>
                            {on ? <Check size={18} style={{ color: 'var(--success)' }} /> : <Minus size={16} className="muted" />}
                          </button>
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            ))}
          </table>
        </div>
      )}
      {save.error ? <div className="banner banner--danger" role="alert" style={{ marginTop: 'var(--space-4)' }}>{save.error instanceof ApiError ? save.error.message : 'Could not save.'}</div> : null}
      <ConfirmDialog open={confirmDelete} title={`Delete the ${role} role?`} confirmLabel="Delete role" destructive busy={del.isPending}
        message="The role is removed from Keycloak together with its permissions. This only works when nobody has the role."
        onCancel={() => setConfirmDelete(false)} onConfirm={() => del.mutate()} />
      {saved ? <div className="banner banner--success" role="status" style={{ marginTop: 'var(--space-4)' }}>Saved. The change is in the audit log and applies to the next request.</div> : null}
    </Card>
  )
}
