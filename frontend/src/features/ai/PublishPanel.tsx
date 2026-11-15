import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, CircleAlert, Clipboard, ExternalLink, FlaskConical, Pencil, Send, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, unwrap } from '@/api/client'
import type { AiAccess, PublishResult } from '@/api/types'
import { plural } from '@/lib/format'
import { Button } from '@/ui/Button'
import { ConfirmDialog } from '@/ui/ConfirmDialog'
import { FacebookIcon, InstagramIcon, LinkedinIcon } from '@/ui/BrandIcons'
import { InfoNote } from '@/ui/InfoNote'
import { StatusPill } from '@/ui/StatusPill'
import { useToast } from '@/ui/Toast'
import { channelView } from './channelState'
import type { DraftEditor } from './draftEditor'
import { needsReconnect, resultView } from './publishResult'

const ICON: Record<string, React.ReactNode> = { INSTAGRAM: <InstagramIcon />, LINKEDIN: <LinkedinIcon />, FACEBOOK: <FacebookIcon /> }
const RESULT_ICON = { check: <Check size={16} aria-hidden="true" />, x: <X size={16} aria-hidden="true" />, alert: <CircleAlert size={16} aria-hidden="true" />, flask: <FlaskConical size={16} aria-hidden="true" /> }

type Props = {
  editor: DraftEditor
  suggested: string[]
  access?: AiAccess
  onEdit: () => void
  results: PublishResult[]
  onResults: (r: PublishResult[]) => void
}

export function PublishPanel({ editor, suggested, access, onEdit, results, onResults }: Props) {
  const qc = useQueryClient()
  const toast = useToast()
  const channels = access?.channels ?? []
  const [picked, setPicked] = useState<string[]>(() => suggested.filter((c) => { const x = channels.find((y) => y.code === c); return x && channelView(x).usable }))
  const [confirming, setConfirming] = useState(false)
  const sending = useRef(false)   // a double click must not send two requests

  const byCode = new Map(results.map((r) => [r.channel, r]))
  const done = (code: string) => { const r = byCode.get(code); return !!r && resultView(r).blocksRetry }
  const selectable = picked.filter((c) => !done(c))
  const anyUsable = channels.some((c) => channelView(c).usable)

  const publish = useMutation({
    mutationFn: async () => {
      if (editor.dirty) await editor.save()   // what you confirmed is what is posted
      return unwrap(api.POST('/api/ai/drafts/{id}/publish', { params: { path: { id: editor.draft.id } }, body: { channels: selectable } }))
    },
    onSuccess: (r) => { onResults([...results.filter((x) => !r.some((y) => y.channel === x.channel)), ...r]); void qc.invalidateQueries({ queryKey: ['social', 'history'] }) },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not publish.'),
    onSettled: () => { sending.current = false; setConfirming(false) },
  })
  const confirm = () => { if (sending.current) return; sending.current = true; publish.mutate() }
  const toggle = (code: string) => setPicked((p) => (p.includes(code) ? p.filter((c) => c !== code) : [...p, code]))
  const copy = async () => {
    try { await navigator.clipboard.writeText(editor.text); toast.success('Post text copied.') } catch { toast.error('Could not copy. Select the text in the draft and copy it yourself.') }
  }
  const targets = selectable.map((c) => channels.find((x) => x.code === c)).filter((c) => !!c)

  return (
    <section className="publish" aria-label="Publish this draft">
      <h3 className="label-caps">Publish to</h3>
      <div className="publish__channels">
        {channels.map((c) => {
          const v = channelView(c)
          const on = picked.includes(c.code)
          return (
            <div key={c.code} className="channel-wrap">
              <button type="button" className="channel" aria-pressed={on && v.usable} disabled={!v.usable || done(c.code)} data-on={on && v.usable} onClick={() => toggle(c.code)}>
                {ICON[c.provider ?? '']}<span>{c.name}</span>{on && v.usable && !done(c.code) ? <Check size={16} aria-hidden="true" /> : null}
                {v.note ? <span className="channel__note">{v.note}</span> : null}
              </button>
              {v.action ? <Link className="channel__link" to="/accounts">{v.action === 'connect' ? 'Connect' : 'Reconnect'}<span className="sr-only"> {c.name}</span></Link> : null}
            </div>
          )
        })}
      </div>
      {channels.length === 0 ? <p className="muted">No channels yet.</p> : null}
      <div className="row" style={{ flexWrap: 'wrap', marginTop: 'var(--space-4)' }}>
        <Button variant="primary" icon={<Send size={18} />} loading={publish.isPending} disabled={selectable.length === 0 || !editor.valid} onClick={() => setConfirming(true)}>
          Publish to {plural(selectable.length, 'channel')}
        </Button>
        <Button icon={<Pencil size={18} />} onClick={onEdit}>Edit draft</Button>
        {!anyUsable || results.some((r) => r.status === 'UNKNOWN') ? (<>
          <Button icon={<Clipboard size={18} />} onClick={() => void copy()}>Copy text</Button>
          <a className="btn btn--secondary" href="https://www.linkedin.com/feed/" target="_blank" rel="noopener noreferrer"><ExternalLink size={18} aria-hidden="true" />Open LinkedIn<span className="sr-only"> (opens in a new tab)</span></a>
        </>) : null}
      </div>
      {!anyUsable ? <p className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 'var(--space-2)' }}>No account is connected for you yet. Connect LinkedIn, or copy the text and post it yourself.</p> : null}

      {results.length ? (
        <ul className="results" aria-label="Publishing results">
          {results.map((r) => {
            const v = resultView(r)
            const name = channels.find((c) => c.code === r.channel)?.name ?? r.channel
            return (
              <li key={r.channel} className="result">
                <div className="row"><strong>{name}</strong><StatusPill tone={v.tone}>{RESULT_ICON[v.icon]}{v.label}</StatusPill></div>
                <p>{r.message && r.status !== 'FAILED' ? r.message : v.advice}</p>
                <div className="row">
                  {v.link ? <a href={v.link} target="_blank" rel="noopener noreferrer">View post<span className="sr-only"> on LinkedIn (opens in a new tab)</span></a> : null}
                  {needsReconnect(r) ? <Link to="/accounts">Reconnect LinkedIn</Link> : null}
                </div>
              </li>
            )
          })}
        </ul>
      ) : null}

      <ConfirmDialog open={confirming} title="Publish this post?" busy={publish.isPending} confirmLabel={`Publish to ${plural(selectable.length, 'account')}`}
        message="This is the exact text that will be posted. It is logged with your name." onCancel={() => setConfirming(false)} onConfirm={confirm}>
        <pre className="publish__preview" aria-label="Post text">{editor.text}</pre>
        <p className="label-caps">Posting to</p>
        <ul className="plain-list">{targets.map((c) => <li key={c.code}>{c.name}{c.accountName ? ` · ${c.accountName}` : ''}{c.status === 'SIMULATED' ? ' (simulated: nothing is posted)' : ''}</li>)}</ul>
        {editor.dirty ? <p className="muted">Your unsaved edits are saved first.</p> : null}
      </ConfirmDialog>
      <div style={{ marginTop: 'var(--space-3)' }}><InfoNote>Publishing needs a role with publish access. Every post is logged with your name.</InfoNote></div>
    </section>
  )
}

/** For people whose role cannot publish: no channel picker, only a way to take the text away. */
export function CopyOnly({ text, onEdit }: { text: string; onEdit: () => void }) {
  const toast = useToast()
  const copy = async () => { try { await navigator.clipboard.writeText(text); toast.success('Post text copied.') } catch { toast.error('Could not copy. Select the text in the draft and copy it yourself.') } }
  return (
    <section className="publish" aria-label="Use this draft">
      <p className="muted">Your role cannot publish to social accounts. Copy the text and post it yourself, or ask for a change.</p>
      <div className="row" style={{ flexWrap: 'wrap' }}>
        <Button icon={<Clipboard size={18} />} onClick={() => void copy()}>Copy text</Button>
        <Button icon={<Pencil size={18} />} onClick={onEdit}>Edit draft</Button>
      </div>
    </section>
  )
}
