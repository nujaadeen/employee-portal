import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import type { Draft } from '@/api/types'
import { captionCounter } from './counter'

const TAG = /^[\p{L}\p{N}_]{1,50}$/u

/** "#Baracel, QR_Pay  Autumn" -> ["Baracel", "QR_Pay", "Autumn"] */
export function parseHashtags(text: string): string[] {
  return text.split(/[\s,]+/).map((t) => t.replace(/^#+/, '')).filter(Boolean)
}
export const hashtagsError = (tags: string[]) => tags.length > 30 ? 'Use at most 30 hashtags.' : tags.find((t) => !TAG.test(t)) ? 'A hashtag has letters, digits or underscores only.' : null
export const tagsToText = (tags: string[] | undefined) => (tags ?? []).map((t) => t.replace(/^#/, '')).join(' ')

/** The exact text that is posted: headline, caption and hashtags. */
export function postText(headline: string, caption: string, tags: string[]) {
  return [headline.trim(), caption.trim(), tags.map((t) => `#${t}`).join(' ')].filter(Boolean).join('\n\n')
}

/** Editing state of one draft. Saves with PATCH /api/ai/drafts/{id}. */
export function useDraftEditor(draft: Draft) {
  const [headline, setHeadline] = useState(draft.headline)
  const [caption, setCaption] = useState(draft.caption)
  const [tagsText, setTagsText] = useState(tagsToText(draft.hashtags))
  const [saved, setSaved] = useState({ headline: draft.headline, caption: draft.caption, tags: tagsToText(draft.hashtags) })
  const [justSaved, setJustSaved] = useState(false)
  const tags = parseHashtags(tagsText)
  const counter = captionCounter(caption, draft.platform)
  const tagsError = hashtagsError(tags)
  const headlineError = headline.trim() === '' ? 'The headline cannot be empty.' : headline.length > 200 ? 'The headline can have at most 200 characters.' : null
  const captionError = caption.trim() === '' ? 'The caption cannot be empty.' : counter.state === 'over' ? counter.message : null
  const dirty = headline !== saved.headline || caption !== saved.caption || tagsText !== saved.tags
  const valid = !headlineError && !captionError && !tagsError

  const m = useMutation({
    mutationFn: () => unwrap(api.PATCH('/api/ai/drafts/{id}', { params: { path: { id: draft.id } }, body: { headline: headline.trim(), caption: caption.trim(), hashtags: tags } })),
    onSuccess: () => { setSaved({ headline, caption, tags: tagsText }); setJustSaved(true) },
  })
  return {
    draft, headline, caption, tagsText, tags, counter, headlineError, captionError, tagsError, dirty, valid, justSaved,
    setHeadline: (v: string) => { setJustSaved(false); setHeadline(v) },
    setCaption: (v: string) => { setJustSaved(false); setCaption(v) },
    setTagsText: (v: string) => { setJustSaved(false); setTagsText(v) },
    save: () => m.mutateAsync(),
    saving: m.isPending,
    saveError: m.error ? (m.error instanceof ApiError ? m.error.message : 'The draft could not be saved.') : null,
    text: postText(headline, caption, tags),
  }
}
export type DraftEditor = ReturnType<typeof useDraftEditor>
