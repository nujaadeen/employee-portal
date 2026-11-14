import { useQuery } from '@tanstack/react-query'
import { Info, Plus, Search, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { api, unwrap } from '@/api/client'
import type { EmployeeItem } from '@/api/types'
import { usePermissions } from '@/auth/me'
import { plural } from '@/lib/format'
import { Avatar } from '@/ui/Avatar'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { EmptyState } from '@/ui/EmptyState'
import { PageHeader } from '@/ui/PageHeader'
import { SegmentedTabs } from '@/ui/SegmentedTabs'
import { SkeletonRows } from '@/ui/Skeleton'
import { ApproveModal, EditModal, InviteModal } from './HrModals'
import { ProfilePanel } from './ProfilePanel'
import { EmployeeStatus, roleLabel } from './StatusLabel'
import { defaultTab, employeeTabs } from './tabs'
import type { EmployeeTab } from './tabs'
import './employees.css'

function useList(scope: EmployeeTab, enabled: boolean, q: string, status?: string) {
  return useQuery({
    queryKey: ['employees', scope, q, status ?? ''],
    queryFn: () => unwrap(api.GET('/api/employees', { params: { query: { scope, q: q || undefined, status: (status || undefined) as 'ACTIVE' | undefined, size: 100 } } })),
    enabled,
  })
}

const Person = ({ e }: { e: EmployeeItem }) => (
  <span className="person"><Avatar name={e.name!} /><span style={{ minWidth: 0 }}><span className="person__name">{e.name}</span><span className="person__sub">{e.email}</span></span></span>
)

export function EmployeesPage() {
  const { has, me } = usePermissions()
  const tabs = employeeTabs(has)
  const [tab, setTab] = useState<EmployeeTab>(defaultTab(has))
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [selected, setSelected] = useState<string | null>(null)
  const [invite, setInvite] = useState(false)
  const [edit, setEdit] = useState<EmployeeItem | null>(null)
  const [approve, setApprove] = useState<EmployeeItem | null>(null)

  const team = useList('team', has('employee:team'), tab === 'team' ? search : '')
  const dir = useList('directory', has('employee:directory'), tab === 'directory' ? search : '')
  const all = useList('all', has('employee:records'), tab === 'all' ? search : '', status)
  const waiting = useList('all', has('employee:manage'), '', 'PENDING_APPROVAL')
  const approvals = useQuery({ queryKey: ['leave', 'approvals'], enabled: has('leave:approve'), queryFn: () => unwrap(api.GET('/api/leave/requests', { params: { query: { scope: 'approvals', status: 'ALL' } } })) })

  const current = tab === 'team' ? team : tab === 'all' ? all : dir
  const items = current.data?.items ?? []
  const sel = tab === 'directory' ? null : (selected ?? items[0]?.id ?? null)

  const counts = all.data?.statusCounts as Record<string, number> | undefined
  const tabItems = useMemo(() => tabs.map((t) => ({
    value: t.value,
    label: <>{t.label} {t.value === 'team' ? team.data?.total ?? '' : t.value === 'directory' ? dir.data?.total ?? '' : all.data?.total ?? ''}</>,
  })), [tabs, team.data?.total, dir.data?.total, all.data?.total])

  const out = (team.data?.items ?? []).filter((e) => e.status === 'ON_LEAVE')
  const toApprove = (approvals.data ?? []).filter((r) => r.status === 'PENDING').length
  const sub = tab === 'all' ? `Directory and HR records · ${plural(all.data?.total ?? 0, 'person', 'people')}` : 'Your team and the company directory'
  const dept = me?.department ?? ''

  return (
    <>
      <PageHeader title="Employees" subtitle={sub}
        actions={has('employee:manage') ? <Button variant="primary" icon={<Plus size={18} />} onClick={() => setInvite(true)}>Invite employee</Button>
          : has('leave:request') ? <Button variant="primary" to="/leave" icon={<Plus size={18} />}>Request leave</Button> : null} />

      {tab === 'team' ? (
        <div className="grid emp-stats">
          <Card as="div"><div className="label-caps">Direct reports</div><div className="stat-value">{team.data?.total ?? '–'}</div><div className="muted">{dept ? `in ${dept}` : 'reporting to you'}</div></Card>
          <Card as="div"><div className="label-caps">Out today</div><div className="stat-value">{out.length}</div><div className="muted">{out.length ? out.map((o) => o.name).join(', ') : 'Everyone is in'}</div></Card>
          {has('leave:approve') ? <Card as="div"><div className="label-caps">Leave to approve</div><div className="stat-value">{toApprove}</div><div className="muted">waiting for you</div></Card> : null}
        </div>
      ) : null}

      {has('employee:manage') && tab === 'all' && (waiting.data?.items ?? []).length > 0 ? (
        <Card title="Waiting for approval" subtitle="New sign-ups have no role until you approve them." className="emp-waiting">
          <ul className="plain-list">
            {waiting.data!.items!.map((p) => (
              <li key={p.id} className="row emp-waiting__row">
                <Person e={p} /><span className="grow" />
                <Button variant="primary" aria-label={`Approve ${p.name}`} onClick={() => setApprove(p)}>Approve</Button>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <div className="emp-main">
        <Card as="section" aria-label="People" className="emp-list">
          <div className="row emp-toolbar">
            <SegmentedTabs label="Employee views" tabs={tabItems} value={tab} onChange={(t) => { setTab(t); setSearch(''); setSelected(null) }} />
            <div className="search grow">
              <Search size={20} aria-hidden="true" />
              <input className="field__control" type="search" aria-label="Search people" placeholder={tab === 'team' ? 'Search your team' : 'Search name, email or team'} value={search} onChange={(e) => setSearch(e.target.value)} />
            </div>
          </div>
          {tab === 'all' && counts ? (
            <div className="emp-chips">
              {[['', `All ${counts.ALL ?? 0}`], ['ACTIVE', `Active ${counts.ACTIVE ?? 0}`], ['ON_LEAVE', `On leave ${counts.ON_LEAVE ?? 0}`], ['ONBOARDING', `Onboarding ${counts.ONBOARDING ?? 0}`]].map(([v, l]) => (
                <button key={v} type="button" className="tab" aria-pressed={status === v} data-on={status === v} onClick={() => { setStatus(v!); setSelected(null) }}>{l}</button>
              ))}
            </div>
          ) : null}
          {tab === 'team' ? <p className="note" style={{ margin: 'var(--space-4) 0' }}><Info size={16} aria-hidden="true" /><span>Showing the people who report to you. Other managers&apos; teams are not visible.</span></p> : null}
          {current.isPending ? <SkeletonRows rows={6} label="Loading people" /> : current.isError ? (
            <p className="note note--danger" role="alert">Could not load people. {current.error.message} <Button variant="ghost" onClick={() => { void current.refetch() }}>Try again</Button></p>
          ) : items.length === 0 ? (
            <EmptyState icon={<Users size={24} />} title={search ? 'No one matches your search' : tab === 'team' ? 'No direct reports' : 'No people found'}>
              {tab === 'team' ? 'People who report to you will show up here.' : 'Try a different search.'}
            </EmptyState>
          ) : (
            <div className="table-wrap">
              <table className="table" aria-label={tab === 'team' ? 'My team' : tab === 'all' ? 'All employees' : 'Directory'}>
                <thead>
                  {tab === 'directory' ? <tr><th>Employee</th><th>Position</th><th>Department</th></tr>
                    : tab === 'team' ? <tr><th>Employee</th><th>Position</th><th>Annual leave left</th><th>Status</th></tr>
                      : <tr><th>Employee</th><th>Position</th><th>Role</th><th>Manager</th><th>Status</th></tr>}
                </thead>
                <tbody>
                  {items.map((e) => (
                    <tr key={e.id} aria-selected={tab !== 'directory' && sel === e.id}>
                      <td>{tab === 'directory' ? <Person e={e} /> : <button type="button" className="row-btn" onClick={() => setSelected(e.id!)} aria-label={`Show ${e.name}`}><Person e={e} /></button>}</td>
                      <td>{e.jobTitle}{tab !== 'team' && e.department ? <div className="muted">{e.department}</div> : null}</td>
                      {tab === 'directory' ? <td>{e.department}</td> : null}
                      {tab === 'team' ? <td>{e.annualLeaveLeft == null ? '–' : plural(e.annualLeaveLeft, 'day')}</td> : null}
                      {tab === 'all' ? <><td><span className="pill pill--info">{roleLabel(e.role)}</span></td><td>{e.managerName ?? '–'}</td></> : null}
                      {tab !== 'directory' ? <td><EmployeeStatus status={e.status} /></td> : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
        {sel ? <div className="emp-panel"><ProfilePanel id={sel} myId={me?.id} onEdit={() => { const p = items.find((i) => i.id === sel); if (p) setEdit(p) }} /></div> : null}
      </div>

      <InviteModal open={invite} onClose={() => setInvite(false)} />
      <ApproveModal person={approve} onClose={() => setApprove(null)} />
      {edit ? <EditModal key={edit.id} person={edit} onClose={() => setEdit(null)} /> : null}
    </>
  )
}
