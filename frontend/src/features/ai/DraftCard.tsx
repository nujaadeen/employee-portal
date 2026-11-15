import { Save } from 'lucide-react'
import { useId } from 'react'
import type { ChatSource } from '@/api/types'
import { Button } from '@/ui/Button'
import { Field, TextArea } from '@/ui/Field'
import { Sources } from './Answer'
import type { DraftEditor } from './draftEditor'

/** The generated post: a preview in the look of the sample, with editable headline, caption and hashtags. */
export function DraftCard({ editor, sources, messageKey, readOnly }: { editor: DraftEditor; sources: ChatSource[]; messageKey: string; readOnly?: boolean }) {
  const id = useId()
  const c = editor.counter
  return (
    <article className="draft" aria-label="Draft post">
      <div className="draft__art" aria-hidden="true"><span className="draft__c1" /><span className="draft__c2" /><span className="draft__sq" />
        <div className="draft__text"><div className="draft__head">{editor.headline}</div><div className="draft__sub">{editor.draft.platform === 'LINKEDIN' ? 'LinkedIn post' : editor.draft.platform}</div></div>
      </div>
      <div className="draft__body">
        <Field label="Headline" value={editor.headline} maxLength={200} readOnly={readOnly} error={editor.headlineError} onChange={(e) => editor.setHeadline(e.target.value)} />
        <TextArea label="Caption" rows={6} value={editor.caption} readOnly={readOnly} error={editor.captionError} aria-describedby={`${id}-count`} onChange={(e) => editor.setCaption(e.target.value)} />
        <div id={`${id}-count`} className={`counter counter--${c.state}`}>
          <span>{c.label}<span className="sr-only"> characters</span></span>
          {c.message && c.state === 'long' ? <span role="status"> {c.message}</span> : null}
        </div>
        <Field label="Hashtags" hint="Separate with spaces. Letters, digits and underscores only." value={editor.tagsText} readOnly={readOnly} error={editor.tagsError} onChange={(e) => editor.setTagsText(e.target.value)} />
        {!readOnly ? (
          <div className="row" style={{ marginTop: 'var(--space-3)', flexWrap: 'wrap' }}>
            <Button icon={<Save size={18} />} loading={editor.saving} disabled={!editor.dirty || !editor.valid} onClick={() => { void editor.save().catch(() => undefined) }}>Save changes</Button>
            {editor.justSaved ? <span className="muted" role="status">Saved</span> : editor.dirty ? <span className="muted">Unsaved changes</span> : null}
          </div>
        ) : null}
        {editor.saveError ? <p className="field__error" role="alert">{editor.saveError}</p> : null}
        <Sources sources={sources} messageKey={messageKey} />
      </div>
    </article>
  )
}
