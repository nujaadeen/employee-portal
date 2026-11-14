import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Mail } from 'lucide-react'
import { api, ApiError, unwrap } from '@/api/client'
import { Card } from '@/ui/Card'
import { SkeletonRows } from '@/ui/Skeleton'
import { SummaryList } from '@/ui/SummaryList'
import { Toggle } from '@/ui/Toggle'
import { FacebookIcon, GithubIcon, GoogleIcon } from '@/ui/BrandIcons'

const ICON: Record<string, React.ReactNode> = { google: <GoogleIcon />, github: <GithubIcon />, facebook: <FacebookIcon />, password: <Mail size={20} /> }
const NOTE: Record<string, string> = {
  google: 'Sign in with a Google account', github: 'Sign in with a GitHub account', facebook: 'Sign in with a Facebook account',
  password: 'Managed in Keycloak (password reset, verification)',
}

export function SignInMethods() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['access', 'idps'], queryFn: () => unwrap(api.GET('/api/access/identity-providers')) })
  const settings = useQuery({ queryKey: ['access', 'settings'], queryFn: () => unwrap(api.GET('/api/access/settings')) })
  const m = useMutation({
    mutationFn: ({ alias, enabled }: { alias: string; enabled: boolean }) => unwrap(api.PUT('/api/access/identity-providers', { body: { providers: [{ alias, enabled }] } })),
    onSuccess: (data) => qc.setQueryData(['access', 'idps'], data),
  })
  const s = settings.data
  return (
    <Card as="section" aria-label="Sign-in methods" title="Sign-in methods" subtitle="Identity providers in Keycloak" className="signin-card">
      {q.isPending ? <SkeletonRows rows={4} label="Loading sign-in methods" /> : q.isError ? <p className="note note--danger" role="alert">Could not load sign-in methods. {q.error.message}</p> : (
        <ul className="plain-list">
          {(q.data ?? []).map((p) => (
            <li key={p.alias} className="row idp">
              <span className="signin__icon" aria-hidden="true">{ICON[p.alias ?? '']}</span>
              <div className="grow"><strong>{p.displayName}</strong><div className="muted" style={{ fontSize: 'var(--text-xs)' }}>{NOTE[p.alias ?? ''] ?? ''}</div></div>
              <Toggle label={`${p.displayName} sign-in`} checked={!!p.enabled} disabled={!p.editable || m.isPending} onChange={(v) => m.mutate({ alias: p.alias!, enabled: v })} />
            </li>
          ))}
        </ul>
      )}
      {m.error ? <div className="banner banner--danger" role="alert" style={{ marginTop: 'var(--space-3)' }}>{m.error instanceof ApiError ? m.error.message : 'Could not change the provider.'}</div> : null}
      <div style={{ marginTop: 'var(--space-4)' }}>
        <SummaryList label="Sign-up settings" rows={[
          { label: 'New sign-ups get role', value: s?.defaultRoleForNewSignUps ?? '–' },
          { label: 'Approved by', value: s?.approver ?? '–' },
          { label: 'Session length', value: s ? `${s.sessionLengthHours} hours` : '–' },
        ]} />
      </div>
    </Card>
  )
}
