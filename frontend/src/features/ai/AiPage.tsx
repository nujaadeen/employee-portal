import { useQuery } from '@tanstack/react-query'
import { Check, CircleAlert, Lock, Plus, RotateCcw, Send, Sparkles, Square } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, unwrap } from '@/api/client'
import type { ChatMessage } from '@/api/types'
import { roleName } from '@/lib/format'
import { Button } from '@/ui/Button'
import { IconButton } from '@/ui/IconButton'
import { EmptyState } from '@/ui/EmptyState'
import { SkeletonRows } from '@/ui/Skeleton'
import { StatusPill } from '@/ui/StatusPill'
import { AnswerText, Sources } from './Answer'
import { DraftCard } from './DraftCard'
import { useDraftEditor } from './draftEditor'
import { Feedback } from './Feedback'
import { PublishPanel } from './PublishPanel'
import { useChatStream } from './useChatStream'
import './ai.css'

const dayKey = (iso: string) => new Date(iso).toDateString()

function DraftBlock({ message, a, onEdit }: { message: ChatMessage; a: AiAccessData; onEdit: () => void }) {
  const editor = useDraftEditor(message.draft!)
  return (<>
    <DraftCard editor={editor} sources={message.sources ?? []} messageKey={message.id} />
    <PublishPanel key={a ? 'ready' : 'wait'} editor={editor} suggested={message.draft!.suggestedChannels ?? []} access={a} onEdit={onEdit} />
  </>)
}
type AiAccessData = import('@/api/types').AiAccess | undefined

export function AiPage() {
  const { id } = useParams()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const [text, setText] = useState(params.get('prompt') ?? '')
  const end = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const list = useQuery({ queryKey: ['ai', 'conversations'], queryFn: () => unwrap(api.GET('/api/ai/conversations')) })
  const access = useQuery({ queryKey: ['ai', 'access'], queryFn: () => unwrap(api.GET('/api/ai/access')) })
  const conv = useQuery({ queryKey: ['ai', 'conversation', id], enabled: !!id, queryFn: () => unwrap(api.GET('/api/ai/conversations/{id}', { params: { path: { id: id! } } })) })

  const finished = useCallback((cid: string) => { if (cid !== id) nav(`/ai/${cid}`) }, [id, nav])
  const chat = useChatStream(id, finished)
  const st = chat.state
  const streaming = st.status === 'streaming'
  const messages: ChatMessage[] = conv.data?.messages ?? []
  useEffect(() => { end.current?.scrollIntoView?.({ block: 'end' }) }, [messages.length, st.text, st.status])

  const submit = () => { const t = text.trim(); if (t && !streaming) { setText(''); void chat.send(t) } }
  const lastUser = [...messages].reverse().find((m) => m.role === 'USER')

  const today = new Date().toDateString()
  const convs = list.data ?? []
  const groups = [['Today', convs.filter((c) => dayKey(c.updatedAt) === today)], ['Earlier', convs.filter((c) => dayKey(c.updatedAt) !== today)]] as const
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
        </header>
        <div className="sr-only" role="status" aria-live="polite">{streaming ? 'The assistant is writing an answer.' : st.status === 'stopped' ? 'Generation stopped.' : ''}</div>
        <div className="ai__thread" role="log" aria-label="Conversation" aria-live="off" aria-busy={streaming}>
          {!id && st.status === 'idle' ? <EmptyState icon={<Sparkles size={24} />} title="Ask the assistant">Try “Write a LinkedIn post about our autumn campaign”. It only uses knowledge your role allows.</EmptyState> : null}
          {id && conv.isPending ? <SkeletonRows rows={4} label="Loading conversation" /> : null}
          {conv.isError ? <p className="note note--danger" role="alert">Could not load this conversation. {conv.error.message}</p> : null}
          {messages.map((m) => m.role === 'USER' ? <div key={m.id} className="bubble bubble--user">{m.content}</div> : (
            <div key={m.id} className="ai__reply">
              <span className="ai__bot" aria-hidden="true">B</span>
              <div className="grow">
                <AnswerText text={m.content} sources={m.sources ?? []} messageKey={m.id} />
                {m.status === 'STOPPED' ? <p><StatusPill tone="warning">Stopped before the end</StatusPill></p> : null}
                {m.status === 'FAILED' ? <p><StatusPill tone="danger">Answer interrupted</StatusPill></p> : null}
                {m.draft ? <DraftBlock message={m} a={a} onEdit={() => { setText('Change the tone of this draft: '); inputRef.current?.focus() }} /> : <Sources sources={m.sources ?? []} messageKey={m.id} />}
                {m.status === 'COMPLETE' ? <Feedback messageId={m.id} /> : lastUser && m === messages[messages.length - 1] ? <Button icon={<RotateCcw size={16} />} onClick={() => void chat.send(lastUser.content)}>Retry</Button> : null}
              </div>
            </div>
          ))}
          {st.question ? <div className="bubble bubble--user">{st.question}</div> : null}
          {st.status !== 'idle' && (st.text || streaming) ? (
            <div className="ai__reply">
              <span className="ai__bot" aria-hidden="true">B</span>
              <div className="grow">
                <AnswerText text={st.text || '…'} sources={st.sources} messageKey="live" />
                {streaming ? <div className="row"><Button icon={<Square size={14} />} onClick={chat.stop}>Stop generating</Button></div> : null}
                {st.status === 'stopped' ? <p><StatusPill tone="warning">Stopped. The text so far is kept.</StatusPill></p> : null}
                {st.sources.length ? <Sources sources={st.sources} messageKey="live" /> : null}
              </div>
            </div>
          ) : null}
          {st.status === 'error' && st.error ? (
            <div className={`banner banner--${st.error.kind === 'pending' ? 'warning' : 'danger'}`} role="alert">
              <CircleAlert size={18} aria-hidden="true" />
              <div className="grow">
                <strong>{st.error.kind === 'unavailable' ? 'Assistant unavailable' : st.error.kind === 'rate-limit' ? 'Slow down a little' : st.error.kind === 'pending' ? 'Waiting for approval' : 'The answer could not be completed'}</strong>
                <p>{st.error.message}{st.text ? ' The part that arrived is kept above.' : ''}</p>
              </div>
              {st.error.kind !== 'pending' ? <Button icon={<RotateCcw size={16} />} onClick={chat.retry}>Retry</Button> : null}
            </div>
          ) : null}
          <div ref={end} />
        </div>
        <form className="ai__input" onSubmit={(e) => { e.preventDefault(); submit() }}>
          <input ref={inputRef} aria-label="Message" placeholder={id ? 'Ask for a change, or start a new campaign' : 'Ask the assistant'} value={text} onChange={(e) => setText(e.target.value)} />
          <IconButton type="submit" label="Send message" disabled={!text.trim() || streaming} className="ai__send"><Send size={20} /></IconButton>
        </form>
      </section>

      <aside className="ai__side" aria-label="What you can access">
        <div className="label-caps">Your role</div>
        <div className="row" style={{ margin: 'var(--space-3) 0 var(--space-5)', flexWrap: 'wrap' }}>{myRoles.length ? myRoles.map((r) => <StatusPill key={r} tone="info">{roleName(r)}</StatusPill>) : <StatusPill tone="info">Employee</StatusPill>}</div>
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
