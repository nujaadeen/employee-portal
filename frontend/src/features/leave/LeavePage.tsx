import { Plus } from 'lucide-react'
import { Can } from '@/auth/me'
import { PageHeader } from '@/ui/PageHeader'
import { Button } from '@/ui/Button'
import { Approvals } from './Approvals'
import { BalanceCards } from './BalanceCards'
import { MyRequests } from './MyRequests'
import { RequestForm } from './RequestForm'
import { TeamCalendar } from './TeamCalendar'
import { toIso } from '@/lib/format'
import './leave.css'

export function LeavePage() {
  const today = toIso(new Date())
  return (
    <>
      <PageHeader title="Leave" subtitle="Balances, requests and who is away"
        actions={<Button variant="primary" icon={<Plus size={18} />} onClick={() => document.getElementById('request-leave')?.scrollIntoView({ behavior: 'smooth' })}>Request leave</Button>} />
      <BalanceCards />
      <div className="leave-main">
        <TeamCalendar todayIso={today} />
        <RequestForm todayIso={today} />
      </div>
      <div className="stack" style={{ gap: 20 }}>
        <Can permission="leave:approve"><Approvals /></Can>
        <MyRequests />
      </div>
    </>
  )
}
