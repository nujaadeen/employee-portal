import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Check, CircleAlert, FlaskConical, KeyRound, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, ApiError, unwrap } from '@/api/client'
import type { Channel, Connection } from '@/api/types'
import { needsReconnect, resultView } from '@/features/ai/publishResult'
import { usePermission } from '@/auth/me'
import { daysUntil, whenLabel } from '@/lib/format'
import { Button } from '@/ui/Button'
import { Card } from '@/ui/Card'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { EmptyState } from '@/ui/EmptyState'
import { FacebookIcon, InstagramIcon, LinkedinIcon } from '@/ui/BrandIcons'
import { PageHeader } from '@/ui/PageHeader'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { useToast } from '@/ui/Toast'
import { returnMessage } from './returnFlag'
import './accounts.css'

type Target = 'personal' | 'company'

function useConnect() {
  const toast = useToast()
  return useMutation({
    mutationFn: (target: Target) => unwrap(api.POST('/api/social/linkedin/connect', { params: { query: { target } } })),
    onSuccess: (r) => { window.location.assign(r.authorizationUrl) },   // LinkedIn asks for approval, then sends the browser back here
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not start the connection.'),
  })
}

function statusPill(channel: Channel | undefined, c: Connection | undefined) {
  if (!channel) return null
  if (channel.status === 'SIMULATED') return <StatusPill tone="warning"><FlaskConical size={14} aria-hidden="true" />Simulated</StatusPill>
  if (channel.status === 'NOT_AVAILABLE') return <StatusPill>Not enabled yet</StatusPill>
  if (c?.status === 'EXPIRED' || channel.status === 'EXPIRED') return <StatusPill tone="danger"><CircleAlert size={14} aria-hidden="true" />Expired</StatusPill>
  if (c || channel.status === 'CONNECTED') return <StatusPill tone="success"><Check size={14} aria-hidden="true" />Connected</StatusPill>
  return <StatusPill>Not connected</StatusPill>
}

function LinkedInCard({ kind, channel, connection, canManage }: { kind: Target; channel?: Channel; connection?: Connection; canManage: boolean }) {
  const qc = useQueryClient()
  const toast = useToast()
  const connect = useConnect()
  const [confirming, setConfirming] = useState(false)
  const disconnect = useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/social/connections/{id}', { params: { path: { id: connection!.id } } })),
    onSuccess: () => { setConfirming(false); toast.success('Disconnected.'); void qc.invalidateQueries({ queryKey: ['social'] }); void qc.invalidateQueries({ queryKey: ['ai'] }) },
    onError: (e) => { setConfirming(false); toast.error(e instanceof ApiError ? e.message : 'Could not disconnect.') },
  })
  const company = kind === 'company'
  const title = company ? 'Company LinkedIn page' : 'My LinkedIn'
  const status = channel?.status
  const expires = daysUntil(connection?.expiresAt)
  const simulated = status === 'SIMULATED'
  const notEnabled = status === 'NOT_AVAILABLE'
  const expired = connection?.status === 'EXPIRED' || status === 'EXPIRED'
  const canAct = (company ? canManage : true) && !simulated && !notEnabled
  return (
    <Card className="acct">
      <div className="acct__top"><span className="acct__icon"><LinkedinIcon /></span>{statusPill(channel, connection)}</div>
      <h2 className="acct__title">{title}</h2>
      <p className="acct__name muted">{connection ? (company ? `${connection.displayName} · managed by Admin` : connection.displayName) : simulated ? 'No real account (development)' : 'Not connected'}</p>
      {company && connection ? <p className="acct__text muted">You can post here because your role allows publishing to company accounts.</p> : null}
      {!company && connection && expires && !expired ? <p className="acct__text muted">Access expires in {expires}. Reconnect before then to keep publishing.</p> : null}
      {expired ? <p className="acct__text acct__text--bad" role="status">The connection expired. Reconnect to publish again.</p> : null}
      {simulated ? <p className="acct__text muted">LinkedIn publishing is switched off in this environment, so posts are simulated and nothing is sent.</p> : null}
      {notEnabled ? <p className="acct__text muted">Company page publishing needs LinkedIn&rsquo;s approval and is not enabled yet.</p> : null}
      {company && !connection && !canManage && !simulated && !notEnabled ? <p className="acct__text muted">Only an administrator can connect the company page.</p> : null}
      <div className="acct__actions">
        {connection?.profileUrl ? <a className="btn btn--secondary" href={connection.profileUrl} target="_blank" rel="noopener noreferrer"><ArrowRight size={18} aria-hidden="true" />View page<span className="sr-only"> (opens in a new tab)</span></a> : null}
        {canAct && !connection ? <Button variant="primary" icon={<KeyRound size={18} />} loading={connect.isPending} onClick={() => connect.mutate(kind)}>Connect{company ? ' company page' : ''}</Button> : null}
        {canAct && connection ? <Button icon={<KeyRound size={18} />} loading={connect.isPending} onClick={() => connect.mutate(kind)}>Reconnect</Button> : null}
        {canAct && connection && connection.canDisconnect ? <Button variant="danger" onClick={() => setConfirming(true)}>Disconnect</Button> : null}
      </div>
      <ConfirmDialog open={confirming} title={`Disconnect ${title}?`} destructive busy={disconnect.isPending} confirmLabel="Disconnect"
        message={company ? 'Nobody will be able to publish to the company page until it is connected again.' : 'You will not be able to publish to your LinkedIn account until you connect it again. Past posts stay on LinkedIn.'}
        onCancel={() => setConfirming(false)} onConfirm={() => disconnect.mutate()} />
    </Card>
  )
}

function SoonCard({ name, icon }: { name: string; icon: React.ReactNode }) {
  return (
    <Card className="acct">
      <div className="acct__top"><span className="acct__icon">{icon}</span><StatusPill>Coming later</StatusPill></div>
      <h2 className="acct__title">{name}</h2>
      <p className="acct__name muted">Not connected</p>
      <p className="acct__text muted">Drafts can be copied for now. Publishing is not available yet.</p>
    </Card>
  )
}

const PAGE = 10

function History() {
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['social', 'history', page], queryFn: () => unwrap(api.GET('/api/social/history', { params: { query: { page, size: PAGE } } })) })
  const total = q.data?.total ?? 0
  const pages = Math.max(1, Math.ceil(total / PAGE))
  return (
    <Card title="Publishing history" subtitle="Every post made from the assistant is logged with your name" className="acct__history">
      {q.isPending ? <SkeletonRows rows={3} label="Loading history" /> : q.isError ? <p className="note note--danger" role="alert">Could not load the history. {q.error.message}</p> : total === 0 ? (
        <EmptyState icon={<LinkedinIcon />} title="Nothing published yet">When you publish a draft from the assistant it appears here.</EmptyState>
      ) : (<>
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th scope="col">When</th><th scope="col">Channel</th><th scope="col">Post</th><th scope="col">Result</th><th scope="col"><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {q.data.items.map((i) => {
                const v = resultView(i)
                return (
                  <tr key={i.id}>
                    <td className="muted">{whenLabel(i.createdAt)}</td>
                    <td>{i.channelName}</td>
                    <td>{i.headline}</td>
                    <td><StatusPill tone={v.tone}>{v.label}</StatusPill></td>
                    <td className="acct__link">
                      {v.link ? <a href={v.link} target="_blank" rel="noopener noreferrer">View post<span className="sr-only"> {i.headline} (opens in a new tab)</span></a> : null}
                      {needsReconnect(i) ? <a href="#linkedin-cards" onClick={(e) => { e.preventDefault(); document.getElementById('linkedin-cards')?.scrollIntoView?.({ block: 'start' }) }}>Reconnect</a> : null}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        {pages > 1 ? (
          <nav className="pager" aria-label="History pages">
            <Button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
            <span className="muted" role="status">Page {page + 1} of {pages}</span>
            <Button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</Button>
          </nav>
        ) : null}
      </>)}
    </Card>
  )
}

export function AccountsPage() {
  const [params, setParams] = useSearchParams()
  const canManage = usePermission('access:manage')
  const canCompany = usePermission('social:publish:company')
  const canPersonal = usePermission('social:publish:personal')
  const connections = useQuery({ queryKey: ['social', 'connections'], queryFn: () => unwrap(api.GET('/api/social/connections')) })
  const access = useQuery({ queryKey: ['ai', 'access'], queryFn: () => unwrap(api.GET('/api/ai/access')) })
  const [message] = useState(() => returnMessage(params))
  // the flag has been read: take it out of the address so a reload does not show it again
  useEffect(() => { if (params.get('linkedin')) { const p = new URLSearchParams(params); ['linkedin', 'target', 'reason'].forEach((k) => p.delete(k)); setParams(p, { replace: true }) } }, [params, setParams])

  const channels = access.data?.channels ?? []
  const company = channels.find((c) => c.provider === 'LINKEDIN' && c.type === 'COMPANY')
  const personal = channels.find((c) => c.provider === 'LINKEDIN' && c.type === 'PERSONAL')
  const list = connections.data ?? []
  return (
    <>
      <PageHeader title="Connected accounts" subtitle="Where the assistant can publish for you" />
      {message ? <div className={`banner banner--${message.tone}`} role={message.tone === 'danger' ? 'alert' : 'status'} style={{ marginBottom: 'var(--space-5)' }}>{message.tone === 'success' ? <Check size={18} aria-hidden="true" /> : message.tone === 'danger' ? <X size={18} aria-hidden="true" /> : null}{message.text}</div> : null}
      {connections.isPending || access.isPending ? <SkeletonRows rows={4} label="Loading accounts" /> : connections.isError || access.isError ? <p className="note note--danger" role="alert">Could not load your accounts. {(connections.error ?? access.error)?.message}</p> : (
        <div className="acct__grid" id="linkedin-cards">
          {canCompany || canManage ? <LinkedInCard kind="company" channel={company} connection={list.find((c) => c.type === 'COMPANY')} canManage={canManage} /> : null}
          {canPersonal ? <LinkedInCard kind="personal" channel={personal} connection={list.find((c) => c.type === 'PERSONAL')} canManage /> : null}
          <SoonCard name="Instagram" icon={<InstagramIcon />} />
          <SoonCard name="Facebook" icon={<FacebookIcon />} />
        </div>
      )}
      <History />
      <p className="muted" style={{ marginTop: 'var(--space-4)' }}>Need to write a post? <Link to="/ai">Ask the assistant</Link>.</p>
    </>
  )
}
