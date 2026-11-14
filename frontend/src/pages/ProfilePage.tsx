import { Check, LogOut, Minus } from 'lucide-react'
import { logout } from '@/auth/oidc'
import { Button } from '@/ui/Button'
import { useMe } from '@/auth/me'
import { PageHeader } from '@/ui/PageHeader'
import { Card } from '@/ui/Card'
import { StatusPill } from '@/ui/StatusPill'
import { SummaryList } from '@/ui/SummaryList'

export function ProfilePage() {
  const { data: me } = useMe()
  if (!me) return null
  return (
    <>
      <PageHeader title="Your profile" subtitle="What Baracel knows about you. HR keeps the record up to date."
        actions={<Button icon={<LogOut size={18} />} onClick={() => { void logout() }}>Sign out</Button>} />
      <div className="grid">
        <Card title={me.name} subtitle={`${me.jobTitle ?? 'No job title yet'}${me.department ? ` · ${me.department}` : ''}`}>
          <SummaryList rows={[
            { label: 'Employee ID', value: <span className="mono">{me.code}</span> },
            { label: 'Email', value: me.email },
            { label: 'Manager', value: me.manager?.name ?? 'None' },
            { label: 'Roles', value: me.roles?.join(', ') ?? '' },
            { label: 'Status', value: me.status },
          ]} />
        </Card>
        <Card title="Sign-in methods" subtitle="Linked in Keycloak">
          <ul className="plain-list">
            {(me.signInMethods ?? []).map((m) => (
              <li key={m.key} className="row" style={{ justifyContent: 'space-between', padding: 'var(--space-3) 0' }}>
                <span>{m.label}</span>
                <span className="row">
                  {m.key === 'password' && m.linked ? <StatusPill tone={m.mfa ? 'success' : 'warning'}>{m.mfa ? 'MFA on' : 'MFA off'}</StatusPill> : null}
                  <StatusPill tone={m.linked ? 'success' : 'neutral'}>{m.linked ? <><Check size={12} aria-hidden="true" /> Linked</> : <><Minus size={12} aria-hidden="true" /> Not linked</>}</StatusPill>
                </span>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  )
}
