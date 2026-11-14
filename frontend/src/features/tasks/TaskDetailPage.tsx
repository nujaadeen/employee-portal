import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Check, FileText, GitBranch, Plus } from 'lucide-react'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError, unwrap } from '@/api/client'
import { Can } from '@/auth/me'
import { giteaLink, useConfig } from '@/config'
import { activityTime, plural, timeAgo, toIso, weekdayDate } from '@/lib/format'
import { Avatar } from '@/ui/Avatar'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { Field, TextArea } from '@/ui/Field'
import { PageHeader } from '@/ui/PageHeader'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { SummaryList } from '@/ui/SummaryList'
import { ErrorPage, NotFound } from '@/pages/StatusPages'
import { CreateBranchModal, LinkPrModal } from './GitModals'
import { LABEL_TONE, nextStatus, statusInfo } from './statuses'
import './tasks.css'
import './detail.css'

const PRIORITY = (p?: string) => (p ? p.charAt(0) + p.slice(1).toLowerCase() : '')

export function TaskDetailPage() {
  const { key = '' } = useParams()
  const qc = useQueryClient()
  const [branch, setBranch] = useState(false)
  const [link, setLink] = useState(false)
  const [sub, setSub] = useState('')
  const [comment, setComment] = useState('')
  const config = useConfig()
  const gitea = config.data?.giteaUrl
  const q = useQuery({ queryKey: ['task', key], queryFn: () => unwrap(api.GET('/api/tasks/{key}', { params: { path: { key } } })) })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ['task', key] }); void qc.invalidateQueries({ queryKey: ['board'] }); void qc.invalidateQueries({ queryKey: ['dashboard'] }) }
  const status = useMutation({ mutationFn: (s: 'BACKLOG' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE') => unwrap(api.PATCH('/api/tasks/{key}/status', { params: { path: { key } }, body: { status: s } })), onSuccess: refresh })
  const toggle = useMutation({ mutationFn: ({ id, done }: { id: number; done: boolean }) => unwrap(api.PATCH('/api/tasks/{key}/subtasks/{id}', { params: { path: { key, id } }, body: { done } })), onSuccess: refresh })
  const addSub = useMutation({ mutationFn: () => unwrap(api.POST('/api/tasks/{key}/subtasks', { params: { path: { key } }, body: { title: sub } })), onSuccess: () => { setSub(''); refresh() } })
  const addComment = useMutation({ mutationFn: () => unwrap(api.POST('/api/tasks/{key}/comments', { params: { path: { key } }, body: { body: comment } })), onSuccess: () => { setComment(''); refresh() } })

  if (q.isError) return q.error instanceof ApiError && q.error.status === 404 ? <NotFound /> : <ErrorPage message={q.error.message} onRetry={() => { void q.refetch() }} />
  if (q.isPending) return <><PageHeader title={key} /><Card as="div"><SkeletonRows rows={6} label="Loading task" /></Card></>
  const d = q.data.task, git = q.data.git, t = d.task
  const st = statusInfo(t.status), next = nextStatus(t.status), nextInfo = next ? statusInfo(next) : null
  const links = git.links ?? [], commits = git.commits ?? []
  const prLinks = links.filter((l) => l.prNumber)
  const mainLink = prLinks[0] ?? links[0]
  const repos = [...new Set(links.map((l) => l.repo))]
  const done = (d.subtasks ?? []).filter((s) => s.done).length

  return (
    <>
      <PageHeader title={t.key} bell={false}
        crumbs={<><Link to="/tasks">{d.sprintName ?? 'Tasks'}</Link><span aria-hidden="true">&gt;</span><span className="mono">{t.key}</span></>}
        subtitle={`Linked to ${plural(repos.length, 'repository', 'repositories')}, ${plural(prLinks.length, 'pull request')} and ${plural(commits.length, 'commit')}`}
        actions={<Can permission="task:edit">
          {nextInfo ? <Button icon={<ArrowRight size={18} />} loading={status.isPending} onClick={() => status.mutate(next!)}>Move to {nextInfo.label.toLowerCase().replace(/^in /, 'In ')}</Button> : null}
          <Button variant="primary" icon={<GitBranch size={18} />} onClick={() => setBranch(true)}>Create branch</Button>
        </Can>} />
      {status.error ? <div className="banner banner--danger" role="alert" style={{ marginBottom: 'var(--space-4)' }}>{status.error.message}</div> : null}
      <div className="detail">
        <div className="detail__main">
          <Card as="section" aria-label="Task">
            <div className="row" style={{ marginBottom: 'var(--space-3)' }}><StatusPill tone={st.tone}>{st.label}</StatusPill>{t.label ? <StatusPill tone={LABEL_TONE[t.label] ?? 'neutral'}>{t.label}</StatusPill> : null}</div>
            <h2 className="detail__title">{t.title}</h2>
            <p className="muted detail__desc">{d.description ?? 'No description yet.'}</p>
          </Card>

          <Card title="Subtasks" action={<span className="muted">{done} of {(d.subtasks ?? []).length} done</span>}>
            {(d.subtasks ?? []).length === 0 ? <p className="muted">No subtasks yet.</p> : (
              <ul className="plain-list">
                {d.subtasks.map((s) => (
                  <li key={s.id} className="subtask">
                    <label className="subtask__label">
                      <input type="checkbox" className="subtask__box" checked={!!s.done} disabled={toggle.isPending}
                        onChange={(e) => toggle.mutate({ id: s.id, done: e.target.checked })} />
                      <span className={s.done ? 'subtask__done' : ''}>{s.title}</span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
            <Can permission="task:edit">
              <form className="row subtask__add" onSubmit={(e: FormEvent) => { e.preventDefault(); if (sub.trim()) addSub.mutate() }}>
                <Field className="grow" label="Add a subtask" value={sub} onChange={(e) => setSub(e.target.value)} />
                <Button type="submit" icon={<Plus size={18} />} loading={addSub.isPending} disabled={!sub.trim()}>Add</Button>
              </form>
            </Can>
          </Card>

          <Card title="Activity">
            <ul className="plain-list">
              {(d.activity ?? []).map((a) => (
                <li key={a.id} className="activity">
                  <Avatar name={a.actor} size="sm" />
                  <div><span><strong>{a.actor}</strong> <span className="muted">{a.message}</span></span><div className="muted activity__time">{activityTime(a.createdAt)}</div></div>
                </li>
              ))}
            </ul>
            <Can permission="task:edit">
              <form className="stack" style={{ marginTop: 'var(--space-4)' }} onSubmit={(e) => { e.preventDefault(); if (comment.trim()) addComment.mutate() }}>
                <TextArea label="Add a comment" value={comment} onChange={(e) => setComment(e.target.value)} />
                <div><Button type="submit" loading={addComment.isPending} disabled={!comment.trim()}>Comment</Button></div>
              </form>
            </Can>
          </Card>
        </div>

        <div className="detail__side">
          <Card title={<span className="label-caps">Details</span>}>
            <SummaryList label="Details" rows={[
              { label: 'Status', value: st.label }, { label: 'Assignee', value: t.assigneeName ?? 'Unassigned' },
              { label: 'Reporter', value: d.reporterName ?? '–' }, { label: 'Sprint', value: d.sprintName ?? 'None' },
              { label: 'Priority', value: PRIORITY(t.priority) },
              { label: 'Due', value: t.dueDate ? (t.dueDate === toIso(new Date()) ? 'Today' : weekdayDate(t.dueDate)) : '–' },
            ]} />
          </Card>

          <Card title={<span className="label-caps">Gitea</span>} action={git.webhookLive ? <StatusPill tone="success">Webhook live</StatusPill> : <StatusPill tone="warning">Webhook not set up</StatusPill>}>
            {links.length === 0 ? <p className="muted">No repository, branch or pull request is linked yet. Create a branch to start.</p> : (
              <div className="stack" style={{ gap: 'var(--space-3)' }}>
                <div><div className="muted">Repository</div><span className="git-chip git-chip--branch mono"><FileText size={14} aria-hidden="true" />{mainLink?.repo}</span></div>
                <div><div className="muted">Branch</div><span className="git-chip git-chip--branch mono"><GitBranch size={14} aria-hidden="true" /><span>{mainLink?.branch}</span></span></div>
                {prLinks.map((l) => (
                  <div key={l.prNumber}>
                    <div className="muted">Pull request</div>
                    <strong>{gitea ? <a href={giteaLink(gitea, `${l.repo}/pulls/${l.prNumber}`)}>#{l.prNumber} {l.prTitle}</a> : `#${l.prNumber} ${l.prTitle}`}</strong>
                    <div style={{ marginTop: 'var(--space-2)' }} className="row">
                      {l.checksStatus ? <StatusPill tone={l.checksStatus === 'PASSING' ? 'success' : l.checksStatus === 'FAILING' ? 'danger' : 'warning'}>{l.checksStatus === 'PASSING' ? <><Check size={12} aria-hidden="true" /> Checks passing</> : l.checksStatus === 'FAILING' ? 'Checks failing' : 'Checks pending'}</StatusPill> : null}
                      <StatusPill tone={l.prState === 'MERGED' ? 'success' : 'info'}>{l.prState === 'MERGED' ? 'Merged' : l.prState === 'CLOSED' ? 'Closed' : 'Open'}</StatusPill>
                    </div>
                  </div>
                ))}
                {commits.length ? (
                  <div><div className="muted" style={{ marginBottom: 'var(--space-2)' }}>Recent commits</div>
                    <ul className="plain-list">{commits.map((c) => (
                      <li key={c.sha} className="commit"><a className="mono commit__sha" href={giteaLink(gitea, `${mainLink?.repo ?? ''}/commit/${c.sha}`)}>{c.shortSha}</a>
                        <div><div>{c.message}</div><div className="muted" style={{ fontSize: 'var(--text-xs)' }}>{c.author} · {timeAgo(c.committedAt)}</div></div></li>))}</ul></div>
                ) : null}
              </div>
            )}
            <div className="row" style={{ marginTop: 'var(--space-4)', flexWrap: 'wrap' }}>
              {mainLink && gitea ? <a className="btn btn--secondary" href={giteaLink(gitea, mainLink.prNumber ? `${mainLink.repo}/pulls/${mainLink.prNumber}` : mainLink.repo)}><ArrowRight size={18} aria-hidden="true" /> Open in Gitea</a> : null}
              <Can permission="task:edit"><Button icon={<Plus size={18} />} onClick={() => setLink(true)}>Link {prLinks.length ? 'another ' : 'a '}PR</Button></Can>
            </div>
          </Card>
        </div>
      </div>
      <CreateBranchModal key={`b-${branch}`} open={branch} onClose={() => setBranch(false)} taskKey={key} projectKey={d.projectKey} defaultRepo={mainLink?.repo ?? ''} />
      <LinkPrModal key={`l-${link}`} open={link} onClose={() => setLink(false)} taskKey={key} projectKey={d.projectKey} defaultRepo={mainLink?.repo ?? ''} />
    </>
  )
}
