import { useQuery } from '@tanstack/react-query'
import { Check, Filter, Plus, SquareCheck, Users } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, unwrap } from '@/api/client'
import type { Board as BoardData, TaskCard } from '@/api/types'
import { Can, usePermissions } from '@/auth/me'
import { useConfig } from '@/config'
import { longRange, plural, timeAgo, weekdayDate } from '@/lib/format'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { EmptyState } from '@/ui/EmptyState'
import { IconButton } from '@/ui/IconButton'
import { PageHeader } from '@/ui/PageHeader'
import { SegmentedTabs } from '@/ui/SegmentedTabs'
import { Select } from '@/ui/Select'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { ErrorPage } from '@/pages/StatusPages'
import { Board } from './Board'
import { CreateTaskModal } from './CreateTaskModal'
import { MembersModal } from './MembersModal'
import { statusInfo } from './statuses'
import './tasks.css'

type View = 'board' | 'list' | 'timeline'

function ListView({ board }: { board: BoardData }) {
  const cards = (board.columns ?? []).flatMap((c) => c.tasks ?? [])
  return (
    <Card as="div"><div className="table-wrap"><table className="table" aria-label="Task list">
      <thead><tr><th>Key</th><th>Title</th><th>Status</th><th>Assignee</th><th>Due</th></tr></thead>
      <tbody>{cards.map((c: TaskCard) => (
        <tr key={c.task.key}><td className="mono"><Link to={`/tasks/${c.task.key}`}>{c.task.key}</Link></td><td>{c.task.title}</td>
          <td><StatusPill tone={statusInfo(c.task.status).tone}>{statusInfo(c.task.status).label}</StatusPill></td><td>{c.task.assigneeName ?? '–'}</td><td>{c.task.dueDate ? weekdayDate(c.task.dueDate) : '–'}</td></tr>
      ))}</tbody></table></div></Card>
  )
}

function Timeline({ board }: { board: BoardData }) {
  const cards = board.columns.flatMap((c) => c.tasks).filter((c) => c.task.dueDate)
    .sort((a, b) => (a.task.dueDate ?? '').localeCompare(b.task.dueDate ?? ''))
  const days = [...new Set(cards.map((c) => c.task.dueDate ?? ''))]
  return (
    <Card as="div" aria-label="Timeline">
      {days.length === 0 ? <EmptyState title="Nothing scheduled">Tasks with a due date show up here.</EmptyState> : days.map((d) => (
        <div key={d} className="timeline__day">
          <h3 className="label-caps">{weekdayDate(d)}</h3>
          <ul className="plain-list">{cards.filter((c) => c.task.dueDate === d).map((c) => (
            <li key={c.task.key} className="row" style={{ padding: 'var(--space-2) 0' }}>
              <Link className="mono" to={`/tasks/${c.task.key}`}>{c.task.key}</Link><span className="grow">{c.task.title}</span>
              <StatusPill tone={statusInfo(c.task.status).tone}>{statusInfo(c.task.status).label}</StatusPill>
            </li>))}</ul>
        </div>
      ))}
    </Card>
  )
}

export function TasksPage() {
  const config = useConfig()
  const { me, has } = usePermissions()
  // HR and administrators manage any project, managers the projects they belong to. The server decides; this only hides the buttons.
  const canManageMembers = has('employee:manage') || has('access:manage') || has('employee:team')
  const [members, setMembers] = useState(false)
  const [view, setView] = useState<View>('board')
  const [mine, setMine] = useState(false)
  const [create, setCreate] = useState(false)
  const [projectKey, setProjectKey] = useState('')
  const [sprintId, setSprintId] = useState('')
  const projects = useQuery({ queryKey: ['projects'], queryFn: () => unwrap(api.GET('/api/projects')) })
  const key = projectKey || projects.data?.[0]?.key || ''
  const boardKey = ['board', key, sprintId] as const
  const board = useQuery({
    queryKey: boardKey, enabled: !!key,
    queryFn: () => unwrap(api.GET('/api/projects/{key}/board', { params: { path: { key }, query: { sprint: sprintId ? Number(sprintId) : undefined } } })),
  })
  if (projects.isError) return <ErrorPage message={projects.error.message} onRetry={() => { void projects.refetch() }} />
  if (projects.isSuccess && projects.data.length === 0) {
    return (<><PageHeader title="Tasks" subtitle="Projects you belong to" /><Card as="div"><EmptyState icon={<SquareCheck size={24} />} title="No projects yet">You are not a member of any project. Ask a project lead to add you.</EmptyState></Card></>)
  }
  const b = board.data
  const sprint = b?.sprint
  const title = sprint ? `${sprint.name} · ${b?.project?.name}` : (b?.project?.name ?? 'Tasks')
  const sub = b ? `${sprint ? `${longRange(sprint.startsOn, sprint.endsOn)} · ` : ''}${plural(b.taskCount ?? 0, 'task')} · ${b.linkedToGitea} linked to Gitea` : undefined
  return (
    <>
      <PageHeader title={title} subtitle={sub}
        actions={<>
          <IconButton label="Only my tasks" aria-pressed={mine} onClick={() => setMine(!mine)} className={mine ? 'icon-btn--on' : ''}><Filter size={20} /></IconButton>
          <Button icon={<Users size={18} />} onClick={() => setMembers(true)}>Members</Button>
          <Can permission="task:create"><Button variant="primary" icon={<Plus size={18} />} onClick={() => setCreate(true)}>Create task</Button></Can>
        </>} />
      <div className="row tasks-bar">
        <SegmentedTabs label="Task views" value={view} onChange={setView} tabs={[{ value: 'board', label: 'Board' }, { value: 'list', label: 'List' }, { value: 'timeline', label: 'Timeline' }]} />
        {(projects.data?.length ?? 0) > 1 ? <Select label="Project" value={key} onChange={(e) => { setProjectKey(e.target.value); setSprintId('') }} options={(projects.data ?? []).map((p) => ({ value: p.key, label: p.name }))} /> : null}
        {(b?.sprints?.length ?? 0) > 1 ? <Select label="Sprint" value={sprintId || String(sprint?.id ?? '')} onChange={(e) => setSprintId(e.target.value)} options={(b?.sprints ?? []).map((s) => ({ value: String(s.id), label: s.name }))} /> : null}
        <span className="grow" />
        {b?.lastSyncedAt ? <span className="muted">Synced with Gitea {timeAgo(b.lastSyncedAt)}</span> : null}
        {config.data ? <StatusPill tone="success"><Check size={14} aria-hidden="true" /> {new URL(config.data.giteaUrl).host}</StatusPill> : null}
      </div>
      {board.isPending || projects.isPending ? <SkeletonRows rows={6} label="Loading board" /> : board.isError ? (
        <ErrorPage message={board.error.message} onRetry={() => { void board.refetch() }} />
      ) : (b?.taskCount ?? 0) === 0 ? (
        <Card as="div"><EmptyState icon={<SquareCheck size={24} />} title="No tasks in this sprint">Create a task to get started.</EmptyState></Card>
      ) : view === 'board' ? <Board board={b!} boardKey={boardKey} mine={mine} meId={me?.id} /> : view === 'list' ? <ListView board={b!} /> : <Timeline board={b!} />}
      {key ? <MembersModal open={members} onClose={() => setMembers(false)} projectKey={key} canManage={canManageMembers} /> : null}
      {key ? <CreateTaskModal open={create} onClose={() => setCreate(false)} projectKey={key} /> : null}
    </>
  )
}
