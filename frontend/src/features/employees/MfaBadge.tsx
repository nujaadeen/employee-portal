import { Building2, ShieldAlert, ShieldCheck } from 'lucide-react'
import { StatusPill } from '@/ui/StatusPill'

export type Mfa = 'ENABLED' | 'DISABLED' | 'PROVIDER_MANAGED'

/** Second-factor state. Always text plus an icon, so it never depends on colour alone. */
export function MfaBadge({ mfa }: { mfa: Mfa }) {
  if (mfa === 'ENABLED') return <StatusPill tone="success"><ShieldCheck size={14} aria-hidden="true" /> MFA on</StatusPill>
  if (mfa === 'DISABLED') return <StatusPill tone="warning"><ShieldAlert size={14} aria-hidden="true" /> MFA off</StatusPill>
  return <StatusPill tone="neutral"><Building2 size={14} aria-hidden="true" /> Managed by provider</StatusPill>
}
