import { useMutation } from '@tanstack/react-query'
import { ThumbsDown, ThumbsUp } from 'lucide-react'
import { useState } from 'react'
import { api, unwrap } from '@/api/client'
import { Button } from '@/ui/Button'
import { IconButton } from '@/ui/IconButton'
import { useToast } from '@/ui/Toast'

type Rating = 'UP' | 'DOWN'

/** Thumbs up or down on an answer, with an optional comment. */
export function Feedback({ messageId }: { messageId: string }) {
  const toast = useToast()
  const [rating, setRating] = useState<Rating | null>(null)
  const [asking, setAsking] = useState(false)
  const [comment, setComment] = useState('')
  const [sent, setSent] = useState(false)
  const m = useMutation({
    mutationFn: (v: { rating: Rating; comment?: string }) => unwrap(api.POST('/api/ai/messages/{id}/feedback', { params: { path: { id: messageId } }, body: v })),
    onError: () => toast.error('Your feedback could not be saved.'),
  })
  const rate = (r: Rating) => {
    setRating(r); setSent(false); setAsking(true)
    m.mutate({ rating: r })
  }
  return (
    <div className="feedback">
      <div className="row" role="group" aria-label="Was this answer helpful?">
        <IconButton label="Helpful" aria-pressed={rating === 'UP'} onClick={() => rate('UP')}><ThumbsUp size={18} /></IconButton>
        <IconButton label="Not helpful" aria-pressed={rating === 'DOWN'} onClick={() => rate('DOWN')}><ThumbsDown size={18} /></IconButton>
        {sent ? <span className="muted" role="status">Thanks for the feedback.</span> : null}
      </div>
      {asking && !sent ? (
        <form className="feedback__form" onSubmit={(e) => { e.preventDefault(); m.mutate({ rating: rating!, comment: comment.trim() || undefined }, { onSuccess: () => { setSent(true); setAsking(false); setComment('') } }) }}>
          <label htmlFor={`fb-${messageId}`}>Add a comment (optional)</label>
          <textarea id={`fb-${messageId}`} className="field__control" rows={2} maxLength={1000} value={comment} onChange={(e) => setComment(e.target.value)} />
          <div className="row"><Button type="submit" variant="primary" loading={m.isPending}>Send feedback</Button><Button onClick={() => { setAsking(false); setSent(true) }}>Skip</Button></div>
        </form>
      ) : null}
    </div>
  )
}
