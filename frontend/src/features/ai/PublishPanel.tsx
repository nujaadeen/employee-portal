import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Clock, Pencil, Send } from 'lucide-react'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import type { AiAccess } from '@/api/types'
import { plural } from '@/lib/format'
import { Button } from '@/ui/Button'
import { InfoNote } from '@/ui/InfoNote'
import { FacebookIcon, InstagramIcon, LinkedinIcon } from '@/ui/BrandIcons'

const ICON: Record<string, React.ReactNode> = { INSTAGRAM: <InstagramIcon />, LINKEDIN: <LinkedinIcon />, FACEBOOK: <FacebookIcon /> }

export function PublishPanel({ draftId, suggested, access, onEdit }: { draftId: string; suggested: string[]; access?: AiAccess; onEdit: () => void }) {
  const qc = useQueryClient()
  const channels = access?.channels ?? []
  const [picked, setPicked] = useState<string[]>(() => suggested.filter((c) => channels.find((x) => x.code === c)?.canPublish))
  const [result, setResult] = useState<string[] | null>(null)
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/ai/drafts/{id}/publish', { params: { path: { id: draftId } }, body: { channels: picked } })),
    onSuccess: (r) => { setResult(r.map((x) => x.channel)); void qc.invalidateQueries({ queryKey: ['ai'] }) },
  })
  const toggle = (code: string) => { setResult(null); setPicked((p) => (p.includes(code) ? p.filter((c) => c !== code) : [...p, code])) }
  const none = !channels.some((c) => c.canPublish)
  return (
    <section className="publish" aria-label="Publish this draft">
      <h3 className="label-caps">Publish to</h3>
      <div className="publish__channels">
        {channels.map((c) => {
          const usable = !!c.canPublish && !!c.connected
          const on = picked.includes(c.code)
          return (
            <button key={c.code} type="button" className="channel" aria-pressed={on} disabled={!usable} data-on={on}
              title={usable ? undefined : c.connected ? 'Your role cannot publish to this channel' : 'Not connected'} onClick={() => toggle(c.code)}>
              {ICON[c.provider ?? '']}<span>{c.name}</span>{on ? <span aria-hidden="true">✓</span> : null}{!usable ? <span className="sr-only"> (not available to you)</span> : null}
            </button>
          )
        })}
      </div>
      <div className="row" style={{ flexWrap: 'wrap', marginTop: 'var(--space-4)' }}>
        <Button variant="primary" icon={<Send size={18} />} loading={m.isPending} disabled={picked.length === 0 || none} onClick={() => m.mutate()}>
          Publish to {plural(picked.length, 'channel')}
        </Button>
        <Button disabled icon={<Clock size={18} />} aria-describedby="schedule-hint">Schedule</Button>
        <Button icon={<Pencil size={18} />} onClick={onEdit}>Edit draft</Button>
      </div>
      <p id="schedule-hint" className="muted" style={{ fontSize: 'var(--text-xs)', marginTop: 'var(--space-2)' }}>Scheduling arrives in Phase 2.</p>
      {m.error ? <div className="banner banner--danger" role="alert" style={{ marginTop: 'var(--space-3)' }}>{m.error instanceof ApiError ? m.error.message : 'Could not publish.'}</div> : null}
      {result ? <div className="banner banner--warning" role="status" style={{ marginTop: 'var(--space-3)' }}><strong>Simulated: nothing was posted.</strong> {plural(result.length, 'channel')} logged with your name: {result.join(', ')}.</div> : null}
      <div style={{ marginTop: 'var(--space-3)' }}><InfoNote>Publishing needs a role with publish access. Every post is logged with your name.</InfoNote></div>
    </section>
  )
}
