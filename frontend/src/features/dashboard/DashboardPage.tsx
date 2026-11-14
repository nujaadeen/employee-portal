import { useQuery } from '@tanstack/react-query'
import { CalendarDays, GitPullRequest, Plus, Sparkles, SquareCheck, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, unwrap } from '@/api/client'
import { Can } from '@/auth/me'
import { dueLabel, greeting, longDate, plural, shortDate, timeAgo, weekdayDate } from '@/lib/format'
import { Avatar } from '@/ui/Avatar'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { EmptyState } from '@/ui/EmptyState'
import { PageHeader } from '@/ui/PageHeader'
import { Skeleton, SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { ErrorPage } from '@/pages/StatusPages'
import type { Tone } from '@/ui/StatusPill'
import './dashboard.css'

const STATUS: Record<string, { label: string; tone: Tone }> = {
  BACKLOG: { label: 'Backlog', tone: 'neutral' },
  IN_PROGRESS: { label: 'In progress', tone: 'info' },
  IN_REVIEW: { label: 'In review', tone: 'warning' },
  DONE: { label: 'Done', tone: 'success' },
}

function Stat({ label, value, note, icon, tone }: { label: string; value: number | string; note: string; icon: React.ReactNode; tone: 'info' | 'warning' | 'neutral' }) {
  return (
    <Card as="div" aria-label={label}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="label-caps">{label}</span>
        <span className={`stat-icon stat-icon--${tone}`} aria-hidden="true">{icon}</span>
      </div>
      <div className="stat-value">{value}</div>
      <div className="muted">{note}</div>
    </Card>
  )
}

export function DashboardPage() {
  const q = useQuery({ queryKey: ['dashboard'], queryFn: () => unwrap(api.GET('/api/dashboard')) })
  if (q.isError) return <ErrorPage message={q.error.message} onRetry={() => { void q.refetch() }} />
  const d = q.data
  const today = d?.today ?? ''
  const out = d?.outToday ?? []
  return (
    <>
      <PageHeader
        title={d ? `${greeting(new Date().getHours())}, ${d.firstName}` : 'Dashboard'}
        subtitle={d ? `${longDate(d.today!)}${d.department ? ` · ${d.department} team` : ''}` : <Skeleton width={220} height={14} />}
        actions={<Can permission="leave:request"><Button variant="primary" to="/leave" icon={<Plus size={18} />}>Request leave</Button></Can>}
      />
      <div className="grid dash-stats">
        <Stat label="Annual leave" tone="info" icon={<CalendarDays size={20} />} value={d?.annualLeave?.left ?? '–'}
          note={d?.annualLeave ? `days left of ${d.annualLeave.entitled}` : 'No balance yet'} />
        <Stat label="Open tasks" tone="warning" icon={<SquareCheck size={20} />} value={d?.openTasks?.count ?? '–'}
          note={`${d?.openTasks?.dueThisWeek ?? 0} due this week`} />
        <Stat label="Needs your review" tone="info" icon={<GitPullRequest size={20} />} value={d?.pullRequestsAwaitingReview ?? '–'} note="pull requests in Gitea" />
        <Stat label="Out today" tone="neutral" icon={<Users size={20} />} value={d ? out.length : '–'}
          note={out.length ? out.map((o) => o.name).join(', ') : 'Everyone is in'} />
      </div>

      <div className="dash-main">
        <Card title="My tasks this week" action={<Link to="/tasks">Open board</Link>} className="dash-wide">
          {q.isPending ? <SkeletonRows rows={4} label="Loading tasks" /> : (d?.nextDue ?? []).length === 0 ? (
            <EmptyState icon={<SquareCheck size={24} />} title="No tasks due">Tasks assigned to you with a due date show up here.</EmptyState>
          ) : (
            <ul className="plain-list">
              {d!.nextDue!.map((t) => {
                const overdue = t.dueDate! < today
                const st = STATUS[t.status ?? 'BACKLOG']!
                return (
                  <li key={t.key} className="dash-task">
                    <Link to={`/tasks/${t.key}`} className="mono dash-task__key">{t.key}</Link>
                    <span className="grow dash-task__title">{t.title}</span>
                    <span className="muted dash-task__due">{dueLabel(t.dueDate!, today) === 'Today' ? weekdayDate(t.dueDate!) : weekdayDate(t.dueDate!)}</span>
                    {t.dueDate === today ? <StatusPill tone="danger">Due today</StatusPill>
                      : overdue ? <StatusPill tone="danger">Overdue</StatusPill> : <StatusPill tone={st.tone}>{st.label}</StatusPill>}
                  </li>
                )
              })}
            </ul>
          )}
        </Card>

        <Card title="Who is out" className="dash-narrow">
          {q.isPending ? <SkeletonRows rows={3} label="Loading who is out" /> : out.length === 0 ? (
            <EmptyState icon={<Users size={24} />} title="Everyone is in">Approved leave for today shows up here.</EmptyState>
          ) : (
            <ul className="plain-list">
              {out.map((o) => (
                <li key={o.employeeId} className="row" style={{ padding: 'var(--space-2) 0' }}>
                  <Avatar name={o.name!} />
                  <div>
                    <strong>{o.name}</strong>
                    <div className="muted">{o.typeName} leave · back {weekdayDate(o.backOn!)}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Announcements" className="dash-wide">
          {q.isPending ? <SkeletonRows rows={2} label="Loading announcements" /> : (d?.announcements ?? []).length === 0 ? (
            <EmptyState title="No announcements">Company news from HR and Engineering shows up here.</EmptyState>
          ) : (
            <ul className="plain-list">
              {d!.announcements!.map((a) => (
                <li key={a.id} className="dash-news">
                  <strong>{a.title}</strong>
                  <div className="muted">Posted by {a.postedBy} · {timeAgo(a.createdAt!)}</div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Can permission="ai:chat">
          <Card title={<span className="row"><Sparkles size={20} aria-hidden="true" style={{ color: 'var(--link)' }} />Ask the assistant</span>} className="dash-narrow">
            <Link to="/ai?prompt=Draft an ad for the new QR product" className="dash-ask">Draft an ad for the new QR product</Link>
            <div className="row" style={{ flexWrap: 'wrap', marginTop: 'var(--space-3)' }}>
              {['Campaign ideas', 'Brand voice', 'Product facts'].map((c) => (
                <Link key={c} to={`/ai?prompt=${encodeURIComponent(c)}`} className="chip">{c}</Link>
              ))}
            </div>
          </Card>
        </Can>
      </div>
      <p className="sr-only">Updated {d ? shortDate(d.today!) : ''}. {plural(out.length, 'person')} out today.</p>
    </>
  )
}
