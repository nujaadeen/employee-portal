import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Lock, Plus, Send, Sparkles } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, ApiError, unwrap } from '@/api/client'
import type { components } from '@/api/schema'
import { Button } from '@/ui/Button'
import { IconButton } from '@/ui/IconButton'
import { EmptyState } from '@/ui/EmptyState'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { DraftCard, Sources } from './DraftCard'
import { PublishPanel } from './PublishPanel'
import './ai.css'

type Message = components['schemas']['MessageDto']

const dayKey = (iso: string) => new Date(iso).toDateString()

export function AiPage() {
  const { id } = useParams()
  const nav = useNavigate()
  const qc = useQueryClient()
  const [params] = useSearchParams()
  const [text, setText] = useState(params.get('prompt') ?? '')
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const end = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const list = useQuery({ queryKey: ['ai', 'conversations'], queryFn: () => unwrap(api.GET('/api/ai/conversations')) })
  const access = useQuery({ queryKey: ['ai', 'access'], queryFn: () => unwrap(api.GET('/api/ai/access')) })
  const conv = useQuery({ queryKey: ['ai', 'conversation', id], enabled: !!id, queryFn: () => unwrap(api.GET('/api/ai/conversations/{id}', { params: { path: { id: id! } } })) })

  const send = useMutation({
    mutationFn: async (message: string) => {
      let cid = id
      if (!cid) cid = (await unwrap(api.POST('/api/ai/conversations', { body: {} }))).id!
      await unwrap(api.POST('/api/ai/conversations/{id}/messages', { params: { path: { id: cid } }, body: { message } }))
      return cid
    },
    onMutate: (m) => { setPending(m); setError(null); setText('') },
    onSuccess: async (cid) => {
      await qc.invalidateQueries({ queryKey: ['ai'] })
      if (cid !== id) nav(`/ai/${cid}`)
    },
    onError: (e, m) => { setError(e instanceof ApiError ? e.message : 'The assistant could not answer.'); setText(m) },
    onSettled: () => setPending(null),
  })
  const messages: Message[] = conv.data?.messages ?? []
  useEffect(() => { end.current?.scrollIntoView?.({ block: 'end' }) }, [messages.length, pending])

  const today = new Date().toDateString()
  const convs = list.data ?? []
  const groups = [['Today', convs.filter((c) => dayKey(c.updatedAt!) === today)], ['Earlier', convs.filter((c) => dayKey(c.updatedAt!) !== today)]] as const
  const a = access.data
  const PORTAL_ROLES = ['MANAGER', 'HR', 'SALES', 'ENGINEER', 'ADMIN']
  const myRoles = (a?.roles ?? []).filter((r) => PORTAL_ROLES.includes(r))

  return (
    <div className="ai">
      <aside className="ai__list" aria-label="Conversations">
        <Button block icon={<Plus size={18} />} onClick={() => { nav('/ai'); inputRef.current?.focus() }}>New chat</Button>
        {list.isPending ? <SkeletonRows rows={4} label="Loading conversations" /> : list.isError ? <p className="note note--danger" role="alert">Could not load conversations.</p>
          : convs.length === 0 ? <p className="muted">No conversations yet.</p> : groups.map(([label, items]) => items.length ? (
            <div key={label}><div className="label-caps ai__group">{label}</div>
              {items.map((c) => <Link key={c.id} to={`/ai/${c.id}`} className="ai__conv" aria-current={c.id === id ? 'page' : undefined}>{c.title}</Link>)}</div>) : null)}
      </aside>

      <section className="ai__chat" aria-label="Chat">
        <header className="ai__head">
          <div><h1>AI assistant</h1><p className="muted">Answers come from company knowledge you have access to</p></div>
          <StatusPill>Simulated assistant · Phase 1</StatusPill>
        </header>
        <div className="ai__thread" aria-live="polite">
          {!id && !pending ? <EmptyState icon={<Sparkles size={24} />} title="Ask the assistant">Try “Write an Instagram and LinkedIn ad for our new merchant QR payments product”. It only uses knowledge your role allows.</EmptyState> : null}
          {id && conv.isPending ? <SkeletonRows rows={4} label="Loading conversation" /> : null}
          {conv.isError ? <p className="note note--danger" role="alert">Could not load this conversation. {conv.error.message}</p> : null}
          {messages.map((m) => m.role === 'USER' ? <div key={m.id} className="bubble bubble--user">{m.content}</div> : (
            <div key={m.id} className="ai__reply">
              <span className="ai__bot" aria-hidden="true">B</span>
              <div className="grow">
                <p>{m.content}</p>
                {m.draft ? (<>
                  <DraftCard draft={m.draft} sources={m.sources ?? []} />
                  <PublishPanel key={a ? 'ready' : 'wait'} draftId={m.draft.id!} suggested={m.draft.suggestedChannels ?? []} access={a} onEdit={() => { setText('Change the tone of this draft: '); inputRef.current?.focus() }} />
                </>) : <Sources sources={m.sources ?? []} />}
              </div>
            </div>
          ))}
          {pending ? <><div className="bubble bubble--user">{pending}</div><p className="muted" role="status">The assistant is writing…</p></> : null}
          {error ? <div className="banner banner--danger" role="alert">{error}</div> : null}
          <div ref={end} />
        </div>
        <form className="ai__input" onSubmit={(e) => { e.preventDefault(); if (text.trim() && !send.isPending) send.mutate(text.trim()) }}>
          <input ref={inputRef} aria-label="Message" placeholder={id ? 'Ask for a change, or start a new campaign' : 'Ask the assistant'} value={text} onChange={(e) => setText(e.target.value)} />
          <IconButton type="submit" label="Send message" disabled={!text.trim() || send.isPending} className="ai__send"><Send size={20} /></IconButton>
        </form>
      </section>

      <aside className="ai__side" aria-label="What you can access">
        <div className="label-caps">Your role</div>
        <div className="row" style={{ margin: 'var(--space-3) 0 var(--space-5)', flexWrap: 'wrap' }}>{myRoles.length ? myRoles.map((r) => <StatusPill key={r} tone="info">{r.charAt(0) + r.slice(1).toLowerCase()}</StatusPill>) : <StatusPill tone="info">Employee</StatusPill>}</div>
        <div className="label-caps">Knowledge you can search</div>
        {access.isPending ? <SkeletonRows rows={5} label="Loading access" /> : access.isError ? <p className="note note--danger" role="alert">Could not load your access.</p> : (
          <ul className="plain-list">
            {(a?.collections ?? []).map((c) => (
              <li key={c.code} className="ai__acc">{c.allowed ? <Check size={16} aria-hidden="true" style={{ color: 'var(--success)' }} /> : <Lock size={16} aria-hidden="true" className="muted" />}
                <span className="grow">{c.name}</span><span className="muted">{c.allowed ? `${c.docCount} docs` : 'No access'}</span></li>
            ))}
          </ul>
        )}
        <div className="label-caps" style={{ marginTop: 'var(--space-5)' }}>Publishing channels</div>
        <ul className="plain-list">
          <li className="ai__acc"><Check size={16} aria-hidden="true" style={{ color: 'var(--success)' }} /><span className="grow">Company accounts ({a?.companyChannels ?? 0})</span><span className="muted">Connected</span></li>
          <li className="ai__acc">{(a?.personalChannels ?? 0) > 0 ? <Check size={16} aria-hidden="true" style={{ color: 'var(--success)' }} /> : <Lock size={16} aria-hidden="true" className="muted" />}<span className="grow">Personal accounts</span><span className="muted">{(a?.personalChannels ?? 0) > 0 ? `${a?.personalChannels} linked` : 'None linked'}</span></li>
        </ul>
      </aside>
    </div>
  )
}
