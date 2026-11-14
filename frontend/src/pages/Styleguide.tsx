import { Calendar, Plus, Search } from 'lucide-react'
import { useState } from 'react'
import { Avatar } from '@/ui/Avatar'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { EmptyState } from '@/ui/EmptyState'
import { Field, TextArea } from '@/ui/Field'
import { IconButton } from '@/ui/IconButton'
import { InfoNote } from '@/ui/InfoNote'
import { Modal } from '@/ui/Modal'
import { PageHeader } from '@/ui/PageHeader'
import { SegmentedTabs } from '@/ui/SegmentedTabs'
import { Select } from '@/ui/Select'
import { Skeleton, SkeletonRows } from '@/ui/Skeleton'
import { SidebarNav } from '@/ui/SidebarNav'
import { StatusPill } from '@/ui/StatusPill'
import { SummaryList } from '@/ui/SummaryList'
import { Toggle } from '@/ui/Toggle'
import { buildNav } from '@/layout/nav'

/** Dev-only page that shows every shared component. Route: /styleguide */
export function Styleguide() {
  const [tab, setTab] = useState('month')
  const [on, setOn] = useState(true)
  const [modal, setModal] = useState(false)
  const [confirm, setConfirm] = useState(false)
  return (
    <>
      <PageHeader title="Styleguide" subtitle="Every shared component in src/ui, in the current theme." bell={false} actions={<Button variant="primary" icon={<Plus size={18} />}>Primary action</Button>} />
      <div className="stack" style={{ gap: 'var(--space-5)' }}>
        <Card title="Buttons and icon buttons" subtitle="One primary action per screen">
          <div className="row" style={{ flexWrap: 'wrap' }}>
            <Button variant="primary">Primary</Button><Button>Secondary</Button><Button variant="danger">Destructive</Button>
            <Button variant="success">Approve</Button><Button variant="ghost">Ghost</Button><Button disabled>Disabled</Button><Button loading>Loading</Button>
            <IconButton label="Calendar"><Calendar size={20} /></IconButton><IconButton label="Alerts" badge={3}><Calendar size={20} /></IconButton>
          </div>
        </Card>
        <Card title="Fields">
          <div className="grid">
            <Field label="Work email" placeholder="name@baracel.example" hint="Helper text under the field" />
            <Field label="With error" defaultValue="oops" error="Something is wrong here" />
            <Select label="Leave type" options={[{ value: 'a', label: 'Annual leave' }, { value: 'b', label: 'Sick leave' }]} />
            <div className="search"><Search size={20} aria-hidden="true" /><Field label="Search" placeholder="Search name, email or team" /></div>
          </div>
          <TextArea label="Note" placeholder="Optional note" />
        </Card>
        <div className="grid">
          <Card title="Pills and tabs">
            <div className="row" style={{ flexWrap: 'wrap', marginBottom: 'var(--space-4)' }}>
              <StatusPill tone="success">Active</StatusPill><StatusPill tone="warning">Pending</StatusPill><StatusPill tone="danger">Due today</StatusPill><StatusPill tone="info">In progress</StatusPill><StatusPill>Backlog</StatusPill><StatusPill mono>feature/bar-142</StatusPill>
            </div>
            <SegmentedTabs label="Calendar view" tabs={[{ value: 'month', label: 'Month' }, { value: 'week', label: 'Week' }]} value={tab} onChange={setTab} />
          </Card>
          <Card title="Summary list"><SummaryList rows={[{ label: 'Working days', value: '1' }, { label: 'Balance after', value: '13 days' }]} /></Card>
          <Card title="Avatar, toggle, note">
            <div className="row" style={{ marginBottom: 'var(--space-4)' }}><Avatar name="Aisha Rahman" /><Avatar name="Omar Haddad" /><Avatar name="Tom Becker" size="sm" /><Toggle checked={on} onChange={setOn} label="Example switch" /></div>
            <InfoNote>Showing the people who report to you.</InfoNote>
          </Card>
        </div>
        <div className="grid">
          <Card title="Empty state"><EmptyState icon={<Calendar size={24} />} title="Nothing here yet">New items will show up here.</EmptyState></Card>
          <Card title="Skeleton"><Skeleton height={28} width="40%" /><div style={{ height: 'var(--space-3)' }} /><SkeletonRows rows={3} /></Card>
          <Card title="Dialogs"><div className="row"><Button onClick={() => setModal(true)}>Open modal</Button><Button variant="danger" onClick={() => setConfirm(true)}>Confirm dialog</Button></div></Card>
          <Card title="Sidebar navigation"><div style={{ background: 'var(--bg-sidebar)', borderRadius: 'var(--radius-md)', padding: 'var(--space-3)' }}><SidebarNav groups={buildNav(() => true)} /></div></Card>
        </div>
      </div>
      <Modal open={modal} title="Modal" onClose={() => setModal(false)} actions={<><Button onClick={() => setModal(false)}>Cancel</Button><Button variant="primary" onClick={() => setModal(false)}>Save</Button></>}><Field label="Name" /></Modal>
      <ConfirmDialog open={confirm} title="Reject this request?" message="The employee will be told." confirmLabel="Reject" destructive onCancel={() => setConfirm(false)} onConfirm={() => setConfirm(false)} />
    </>
  )
}
