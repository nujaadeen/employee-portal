import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Plus } from 'lucide-react'
import { useState } from 'react'
import { api, unwrap } from '@/api/client'
import { env } from '@/env'
import { roleName } from '@/lib/format'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { PageHeader } from '@/ui/PageHeader'
import { SkeletonRows } from '@/ui/Skeleton'
import { NewRoleModal } from './NewRoleModal'
import { RoleMatrixCard } from './RoleMatrixCard'
import { SignInMethods } from './SignInMethods'
import './access.css'

export function AccessPage() {
  const roles = useQuery({ queryKey: ['access', 'roles'], queryFn: () => unwrap(api.GET('/api/access/roles')) })
  const [picked, setPicked] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const names = roles.data?.map((r) => r.name) ?? []
  // a deleted role disappears from the list: fall back to Sales
  const role = picked && names.includes(picked) ? picked : (roles.data?.find((r) => r.name === 'SALES')?.name ?? roles.data?.[0]?.name)
  const current = roles.data?.find((r) => r.name === role)
  const console = `${new URL(env.oidcAuthority).origin}/admin/master/console/#/baracel`
  return (
    <>
      <PageHeader title="Roles and access" subtitle="What each role can see and do across Baracel"
        actions={<a className="btn btn--secondary" href={console} target="_blank" rel="noreferrer"><ArrowRight size={18} aria-hidden="true" /> Open Keycloak</a>} />
      <div className="access">
        <Card as="nav" aria-label="Keycloak roles" className="access__roles">
          <div className="label-caps" style={{ marginBottom: 'var(--space-3)' }}>Keycloak roles</div>
          {roles.isPending ? <SkeletonRows rows={6} label="Loading roles" /> : roles.isError ? <p className="note note--danger" role="alert">Could not load roles. {roles.error.message}</p> : (
            <ul className="plain-list">
              {(roles.data ?? []).map((r) => (
                <li key={r.name}>
                  <button type="button" className="role-btn" aria-current={r.name === role ? 'true' : undefined} onClick={() => setPicked(r.name)}>
                    <span>{r.builtIn ? roleName(r.name) : r.name}{r.builtIn ? '' : <span className="pill pill--neutral role-custom">Custom</span>}</span><span className="muted">{r.people ?? '–'}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <Button block icon={<Plus size={18} />} onClick={() => setCreating(true)} className="role-new">New role</Button>
        </Card>
        {role ? <div className="access__matrix"><RoleMatrixCard key={role} role={role} builtIn={current?.builtIn ?? true} description={current?.description ?? undefined} onDeleted={() => setPicked(null)} /></div> : null}
      </div>
      <NewRoleModal open={creating} onClose={() => setCreating(false)} onCreated={(n) => setPicked(n)} />
      <SignInMethods />
    </>
  )
}
