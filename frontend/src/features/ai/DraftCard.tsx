import { FileText } from 'lucide-react'
import type { components } from '@/api/schema'

type Draft = components['schemas']['DraftDto']
type Source = components['schemas']['SourceDto']

export function Sources({ sources }: { sources: Source[] }) {
  if (!sources.length) return null
  return (
    <div className="sources"><span className="muted">Sources:</span>
      {sources.map((s) => <span key={s.title} className="source mono" title={`From ${s.collection}`}><FileText size={14} aria-hidden="true" />{s.title}</span>)}
    </div>
  )
}

/** Preview of the generated social post. */
export function DraftCard({ draft, sources }: { draft: Draft; sources: Source[] }) {
  return (
    <article className="draft" aria-label="Draft post">
      <div className="draft__art" aria-hidden="true"><span className="draft__c1" /><span className="draft__c2" /><span className="draft__sq" />
        <div className="draft__text"><div className="draft__head">{draft.headline}</div><div className="draft__sub">Accept QR payments at your counter with Baracel.</div></div>
      </div>
      <div className="draft__body">
        <p>{draft.caption} {(draft.hashtags ?? []).map((h) => <span key={h} className="draft__tag">{h} </span>)}</p>
        <Sources sources={sources} />
      </div>
    </article>
  )
}
