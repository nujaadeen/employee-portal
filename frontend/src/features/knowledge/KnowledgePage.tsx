import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock, FileText, Globe, Plus, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import type { ActivityItem, CollectionAdmin } from '@/api/types'
import { roleName, timeAgo, whenLabel } from '@/lib/format'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { EmptyState } from '@/ui/EmptyState'
import { InfoNote } from '@/ui/InfoNote'
import { PageHeader } from '@/ui/PageHeader'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { useToast } from '@/ui/Toast'
import { AddSourceDialog } from './AddSourceDialog'
import { NewCollectionModal } from './NewCollectionModal'
import { activityStatus, collectionStatus, isRunning, sourceTypes, summarize } from './status'
import { toggleRole } from './validation'
import './knowledge.css'

const BUILT_IN = ['EMPLOYEE', 'MANAGER', 'HR', 'SALES', 'ENGINEER', 'ADMIN']
const POLL_MS = 3000

export function KnowledgePage() {
  const qc = useQueryClient()
  const [picked, setPicked] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [creating, setCreating] = useState(false)

  // Activity is polled every 3 seconds while a job runs. React Query stops when the page is left.
  const activity = useQuery({
    queryKey: ['knowledge', 'activity'],
    queryFn: () => unwrap(api.GET('/api/knowledge/activity')),
    refetchInterval: (q) => ((q.state.data ?? []).some((a) => isRunning(a.status)) ? POLL_MS : false),
  })
  const running = (activity.data ?? []).some((a) => isRunning(a.status))
  const overview = useQuery({
    queryKey: ['knowledge', 'collections'],
    queryFn: () => unwrap(api.GET('/api/knowledge/collections')),
    refetchInterval: running ? POLL_MS : false,
  })
  // when a job finishes, counts and statuses are read again once
  const wasRunning = useRef(false)
  useEffect(() => { if (wasRunning.current && !running) void qc.invalidateQueries({ queryKey: ['knowledge'] }); wasRunning.current = running }, [running, qc])

  const list = overview.data?.collections ?? []
  const current = list.find((c) => c.code === picked) ?? list[0]
  const sum = summarize(list)

  return (
    <>
      <PageHeader title="Knowledge base" subtitle="Documents the assistant can search, and who can use them"
        actions={<Button variant="primary" icon={<Plus size={18} />} onClick={() => setAdding(true)} disabled={list.length === 0}>Add source</Button>} />
      {overview.data && !overview.data.aiServiceAvailable ? <div className="banner banner--warning" role="status" style={{ marginBottom: 'var(--space-4)' }}>The AI service is not reachable, so counts and statuses may be out of date. Adding sources is unavailable until it is back.</div> : null}

      <div className="kb__stats">
        <Stat label="Collections" value={overview.isPending ? '–' : String(sum.collections)} sub="searchable by role" />
        <Stat label="Documents" value={overview.isPending ? '–' : String(sum.documents)} sub="across all collections" />
        <Stat label="Last sync" value={sum.lastSync ? whenLabel(sum.lastSync).replace(/^Today /, '') : '–'} sub={sum.lastSync ? (whenLabel(sum.lastSync).startsWith('Today') ? 'today' : 'last indexing run') : 'nothing indexed yet'} />
        <Stat label="Needs attention" value={String(sum.needsAttention)} sub={sum.failedFiles ? `${sum.failedFiles} ${sum.failedFiles === 1 ? 'file' : 'files'} failed to index` : 'nothing failed'} />
      </div>

      <div className="kb__main">
        <Card className="kb__table" title="Collections" action={<Button icon={<Plus size={18} />} onClick={() => setCreating(true)}>New collection</Button>}>
          {overview.isPending ? <SkeletonRows rows={5} label="Loading collections" /> : overview.isError ? <p className="note note--danger" role="alert">Could not load the collections. {overview.error.message}</p> : list.length === 0 ? (
            <EmptyState icon={<FileText size={24} />} title="No collections yet">Create a collection, then add documents to it.</EmptyState>
          ) : (
            <div className="table-wrap">
              <table className="table kb__t">
                <thead><tr><th scope="col">Collection</th><th scope="col">Documents</th><th scope="col">Sources</th><th scope="col">Roles with access</th><th scope="col">Status</th></tr></thead>
                <tbody>
                  {list.map((c) => {
                    const s = collectionStatus(c)
                    return (
                      <tr key={c.code} aria-selected={c.code === current?.code}>
                        <th scope="row"><button type="button" className="kb__pick" aria-pressed={c.code === current?.code} onClick={() => setPicked(c.code)}>{c.name}</button></th>
                        <td>{c.documents}</td>
                        <td className="muted">{sourceTypes(c.sourceTypes)}</td>
                        <td><RoleChips c={c} /></td>
                        <td><StatusPill tone={s.tone}>{s.label}</StatusPill></td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </Card>
        {current ? <Detail key={current.code} c={current} onAdd={() => setAdding(true)} /> : null}
      </div>

      <ActivityCard items={activity.data} loading={activity.isPending} error={activity.isError ? activity.error.message : null} />
      <AddSourceDialog key={adding ? 'open' : 'closed'} open={adding} onClose={() => setAdding(false)} collections={list} initial={current?.code} />
      <NewCollectionModal open={creating} onClose={() => setCreating(false)} onCreated={setPicked} />
    </>
  )
}

function Stat({ label, value, sub }: { label: string; value: string; sub: string }) {
  return (
    <Card className="kb__stat"><div className="label-caps">{label}</div><div className="kb__big">{value}</div><div className="muted">{sub}</div></Card>
  )
}

function RoleChips({ c }: { c: CollectionAdmin }) {
  const roles = c.allEmployees ? ['All employees'] : c.roles.map(roleName)
  return <div className="chips">{roles.map((r) => <StatusPill key={r} tone="info">{r}</StatusPill>)}</div>
}

function Detail({ c, onAdd }: { c: CollectionAdmin; onAdd: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const docs = useQuery({ queryKey: ['knowledge', 'documents', c.code], queryFn: () => unwrap(api.GET('/api/knowledge/collections/{code}/documents', { params: { path: { code: c.code } } })) })
  const roleList = useQuery({ queryKey: ['access', 'roles'], queryFn: () => unwrap(api.GET('/api/access/roles')), retry: false })
  const allRoles = roleList.data?.map((r) => r.name) ?? BUILT_IN
  const [roles, setRoles] = useState<string[]>(c.roles)
  const dirty = [...roles].sort().join() !== [...c.roles].sort().join()
  const [reindexing, setReindexing] = useState(false)
  const [deleting, setDeleting] = useState<{ id: string; title: string } | null>(null)

  const save = useMutation({
    mutationFn: () => unwrap(api.PUT('/api/knowledge/collections/{code}/roles', { params: { path: { code: c.code } }, body: { roles } })),
    onSuccess: () => { toast.success(`Saved. ${roles.length} ${roles.length === 1 ? 'role' : 'roles'} can now search ${c.name}.`); void qc.invalidateQueries({ queryKey: ['knowledge'] }); void qc.invalidateQueries({ queryKey: ['ai'] }) },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not save the roles.'),
  })
  const reindex = useMutation({
    mutationFn: () => unwrap(api.POST('/api/knowledge/collections/{code}/reindex', { params: { path: { code: c.code } } })),
    onSuccess: () => { setReindexing(false); toast.success('Re-indexing started.'); void qc.invalidateQueries({ queryKey: ['knowledge'] }) },
    onError: (e) => { setReindexing(false); toast.error(e instanceof ApiError ? e.message : 'Could not start re-indexing.') },
  })
  const del = useMutation({
    mutationFn: (id: string) => unwrap(api.DELETE('/api/knowledge/collections/{code}/documents/{documentId}', { params: { path: { code: c.code, documentId: id } } })),
    onSuccess: () => { setDeleting(null); toast.success('Document deleted.'); void qc.invalidateQueries({ queryKey: ['knowledge'] }) },
    onError: (e) => { setDeleting(null); toast.error(e instanceof ApiError ? e.message : 'Could not delete the document.') },
  })

  const items = docs.data ?? []
  const failed = items.filter((d) => d.status === 'FAILED')
  const pdfs = items.filter((d) => d.sourceType === 'PDF')
  const drive = items.filter((d) => d.sourceType === 'DRIVE')
  const web = items.filter((d) => d.sourceType === 'WEB')
  const stateOf = (list: typeof items) => (list.some((d) => d.status === 'FAILED') ? { l: 'Failed', t: 'danger' as const } : list.some((d) => d.status !== 'READY') ? { l: 'Indexing', t: 'info' as const } : { l: 'Ready', t: 'success' as const })

  return (
    <Card as="aside" className="kb__detail" title={c.name} subtitle="Sources and roles for this collection" aria-label={`${c.name} details`}>
      <div className="label-caps">Sources</div>
      {docs.isPending ? <SkeletonRows rows={3} label="Loading documents" /> : docs.isError ? <p className="note note--danger" role="alert">Could not load the documents. {docs.error.message}</p> : items.length === 0 ? (
        <p className="muted">No documents yet. Use Add source.</p>
      ) : (<>
        {pdfs.length ? <SourceRow icon={<FileText size={20} />} title={`${pdfs.length} PDF ${pdfs.length === 1 ? 'file' : 'files'}`} note={`${pdfs.reduce((n, d) => n + d.chunkCount, 0)} chunks`} state={stateOf(pdfs)} /> : null}
        {drive.length ? <SourceRow icon={<Globe size={20} />} title={`Google Drive · ${drive.length} ${drive.length === 1 ? 'file' : 'files'}`} note="Only changed files are read again" state={stateOf(drive)} /> : null}
        {web.map((d) => <SourceRow key={d.id} icon={<Globe size={20} />} title={d.sourceRef.replace(/^https?:\/\//, '')} note={`Website · ${timeAgo(d.updatedAt)}`} state={d.status === 'FAILED' ? { l: 'Failed', t: 'danger' } : d.status === 'READY' ? { l: 'Ready', t: 'success' } : { l: 'Indexing', t: 'info' }} />)}
        {failed.length ? (
          <div className="kb__failed" role="group" aria-label="Files that failed">
            <div className="label-caps">Failed to index</div>
            <ul className="plain-list">{failed.map((d) => <li key={d.id}><strong>{d.title}</strong><br /><span className="muted">{d.error ?? 'Unknown error'}</span></li>)}</ul>
          </div>
        ) : null}
        <details className="kb__docs"><summary>All documents ({items.length})</summary>
          <ul className="plain-list">
            {items.map((d) => (
              <li key={d.id} className="kb__doc"><span className="grow">{d.title}<br /><span className="muted">{d.sourceType === 'WEB' ? 'Website' : d.sourceType === 'DRIVE' ? 'Drive' : 'PDF'} · {d.chunkCount} chunks · {d.status === 'READY' ? 'Ready' : d.status === 'FAILED' ? 'Failed' : 'Indexing'}</span></span>
                <button type="button" className="icon-btn" aria-label={`Delete ${d.title}`} onClick={() => setDeleting({ id: d.id, title: d.title })}><Trash2 size={18} /></button></li>
            ))}
          </ul>
        </details>
      </>)}

      <div className="label-caps" style={{ marginTop: 'var(--space-5)' }}>Roles with access</div>
      <div className="chips" role="group" aria-label="Roles with access">
        {allRoles.map((r) => {
          const on = roles.includes(r)
          return <button key={r} type="button" className="role-chip" aria-pressed={on} disabled={r === 'ADMIN'} title={r === 'ADMIN' ? 'Administrators always keep access' : undefined} onClick={() => setRoles((cur) => toggleRole(cur, r))}>{on ? '✓ ' : ''}{roleName(r)}</button>
        })}
      </div>
      <div style={{ marginTop: 'var(--space-3)' }}><InfoNote>This decides who can search the collection. People with a selected role get answers and sources from it in the assistant. Changes apply at once.</InfoNote></div>
      {dirty ? <div className="row" style={{ marginTop: 'var(--space-3)' }}><Button variant="primary" loading={save.isPending} onClick={() => save.mutate()}>Save roles</Button><Button onClick={() => setRoles(c.roles)}>Undo</Button></div> : null}

      <div className="row kb__actions">
        <Button variant="primary" icon={<Plus size={18} />} onClick={onAdd}>Add source</Button>
        <Button icon={<Clock size={18} />} disabled={items.length === 0} onClick={() => setReindexing(true)}>Re-index</Button>
      </div>
      <ConfirmDialog open={reindexing} title={`Re-index ${c.name}?`} message="Every document is read and indexed again. Answers may change while this runs." confirmLabel="Re-index" busy={reindex.isPending} onCancel={() => setReindexing(false)} onConfirm={() => reindex.mutate()} />
      <ConfirmDialog open={!!deleting} title="Delete this document?" destructive busy={del.isPending} confirmLabel="Delete document"
        message={deleting ? `${deleting.title} will no longer be searchable. This cannot be undone.` : ''} onCancel={() => setDeleting(null)} onConfirm={() => deleting && del.mutate(deleting.id)} />
    </Card>
  )
}

function SourceRow({ icon, title, note, state }: { icon: React.ReactNode; title: string; note: string; state: { l: string; t: 'success' | 'danger' | 'info' } }) {
  return (
    <div className="kb__source"><span className="kb__src-icon">{icon}</span><div className="grow"><div><strong>{title}</strong></div><div className="muted">{note}</div></div><StatusPill tone={state.t}>{state.l}</StatusPill></div>
  )
}

function ActivityCard({ items, loading, error }: { items?: ActivityItem[]; loading: boolean; error: string | null }) {
  const [open, setOpen] = useState<string | null>(null)
  return (
    <Card title="Indexing activity" className="kb__activity">
      {loading ? <SkeletonRows rows={3} label="Loading activity" /> : error ? <p className="note note--danger" role="alert">Could not load the activity. {error}</p> : !items || items.length === 0 ? (
        <p className="muted">Nothing has been indexed from here yet.</p>
      ) : (
        <ul className="plain-list" aria-live="polite">
          {items.map((a) => {
            const s = activityStatus(a)
            return (
              <li key={a.jobId} className="kb__act">
                <span className="kb__src-icon"><FileText size={20} aria-hidden="true" /></span>
                <div className="grow">
                  <div><strong>{a.label}</strong></div>
                  <div className="muted">{a.collectionName} · {a.startedBy}{isRunning(a.status) && a.progress > 0 ? ` · ${a.progress}%` : ''} · {whenLabel(a.startedAt)}</div>
                  {isRunning(a.status) ? <progress max={100} value={a.progress} aria-label={`Indexing ${a.label}`} /> : null}
                  {a.error ? <div className="field__error">{a.error}</div> : null}
                  {a.status === 'PARTIAL' || a.status === 'FAILED' ? <button type="button" className="linklike" aria-expanded={open === a.jobId} onClick={() => setOpen(open === a.jobId ? null : a.jobId)}>{open === a.jobId ? 'Hide details' : 'Show what failed'}</button> : null}
                  {open === a.jobId ? <Failures jobId={a.jobId} /> : null}
                </div>
                <StatusPill tone={s.tone}>{s.label}</StatusPill>
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}

function Failures({ jobId }: { jobId: string }) {
  const q = useQuery({ queryKey: ['knowledge', 'job', jobId], queryFn: () => unwrap(api.GET('/api/knowledge/jobs/{id}', { params: { path: { id: jobId } } })) })
  if (q.isPending) return <p className="muted" role="status">Loading…</p>
  if (q.isError) return <p className="note note--danger" role="alert">Could not load the details. {q.error.message}</p>
  const f = q.data.failures
  return f.length ? <ul className="plain-list kb__fail-list">{f.map((x, i) => <li key={i}><span className="mono">{x.sourceRef}</span><br /><span className="muted">{x.error}</span></li>)}</ul> : <p className="muted">{q.data.error ?? 'No details were reported.'}</p>
}
