import { useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import { logout } from '@/auth/oidc'
import { meKey, useMe } from '@/auth/me'
import { pendingApproval } from '@/api/client'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'

function StatusCard({ code, title, children, actions }: { code?: string; title: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <div className="status-page">
      <Card className="status-card" as="div">
        {code ? <div className="status-code" aria-hidden="true">{code}</div> : null}
        <h1>{title}</h1>
        <p className="muted">{children}</p>
        <div className="row">{actions}</div>
      </Card>
    </div>
  )
}

export function WaitingForApproval() {
  const qc = useQueryClient()
  const { data: me } = useMe()
  return (
    <StatusCard title="Waiting for approval"
      actions={<>
        <Button variant="primary" onClick={() => { pendingApproval.set(false); void qc.invalidateQueries({ queryKey: meKey }) }}>Check again</Button>
        <Button onClick={() => { void logout() }}>Sign out</Button>
      </>}>
      {me ? <>Hi {me.name}. </> : null}Your account is created, but HR has not approved it yet. You will get a role and access once they do. Until then only your profile is available.
    </StatusCard>
  )
}

export function Forbidden() {
  return <StatusCard code="403" title="You do not have access" actions={<Button variant="primary" to="/">Back to dashboard</Button>}>Your role does not include this page. If you think it should, ask an administrator.</StatusCard>
}

export function NotFound() {
  return <StatusCard code="404" title="Page not found" actions={<Button variant="primary" to="/">Back to dashboard</Button>}>The page you asked for does not exist, or it was moved.</StatusCard>
}

export function ErrorPage({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <StatusCard title="Something went wrong"
      actions={<>{onRetry ? <Button variant="primary" onClick={onRetry}>Try again</Button> : null}<Link className="btn btn--secondary" to="/">Dashboard</Link></>}>
      {message ?? 'We could not load this page. Try again in a moment.'}
    </StatusCard>
  )
}
