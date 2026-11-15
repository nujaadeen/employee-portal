import { FileText } from 'lucide-react'
import type { ChatSource } from '@/api/types'
import { sourceId, splitCitations } from './citations'

/** Moves to the chip a citation points at and highlights it for a moment. */
function jump(id: string) {
  const el = document.getElementById(id)
  if (!el) return
  el.scrollIntoView?.({ block: 'nearest' })
  el.focus()
  el.dataset.flash = 'true'
  setTimeout(() => { delete el.dataset.flash }, 1500)
}

/** The answer text. [1] style citations become links to the matching source chip. */
export function AnswerText({ text, sources, messageKey }: { text: string; sources: ChatSource[]; messageKey: string }) {
  return (
    <p className="answer">
      {splitCitations(text, sources.length).map((p, i) => p.kind === 'text' ? <span key={i}>{p.text}</span> : (
        <a key={i} className="cite" href={`#${sourceId(messageKey, p.n)}`} aria-label={`Source ${p.n}: ${sources[p.n - 1]?.title ?? ''}`}
          onClick={(e) => { e.preventDefault(); jump(sourceId(messageKey, p.n)) }}>{p.raw}</a>
      ))}
    </p>
  )
}

export function Sources({ sources, messageKey }: { sources: ChatSource[]; messageKey: string }) {
  if (!sources.length) return null
  return (
    <div className="sources" role="list" aria-label="Sources"><span className="muted" aria-hidden="true">Sources:</span>
      {sources.map((s, i) => (
        <span key={i} role="listitem" id={sourceId(messageKey, i + 1)} tabIndex={-1} className="source mono" title={`From ${s.collection}${s.page ? `, page ${s.page}` : ''}`}>
          <FileText size={14} aria-hidden="true" /><span className="source__n">[{i + 1}]</span> {s.title}{s.page ? <span className="muted"> · p. {s.page}</span> : null}
        </span>
      ))}
    </div>
  )
}
