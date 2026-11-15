import type { PublishResult } from '@/api/types'

export type ResultView = {
  /** Words, never colour alone. */
  label: string
  tone: 'success' | 'danger' | 'warning' | 'info'
  icon: 'check' | 'x' | 'alert' | 'flask'
  /** What to do next. */
  advice: string
  /** The link to the post, when there is one. */
  link?: string
  /** After this result the person must not press Publish again. */
  blocksRetry: boolean
}

const REASON: Record<string, { label: string; advice: string }> = {
  TOKEN_EXPIRED: { label: 'Failed: connection expired', advice: 'Reconnect your LinkedIn account, then publish again.' },
  RATE_LIMITED: { label: 'Failed: LinkedIn is limiting posts', advice: 'Wait a while and publish again.' },
  REJECTED: { label: 'Failed: LinkedIn rejected the post', advice: 'Edit the text (for example shorten it) and publish again.' },
  UNKNOWN_ERROR: { label: 'Failed: LinkedIn could not publish', advice: 'Try again. If it keeps failing, tell an administrator.' },
}

/** Maps one result of POST /api/ai/drafts/{id}/publish to the text, icon and next step shown to the person. */
export function resultView(r: Pick<PublishResult, 'status' | 'reason' | 'postUrl'>): ResultView {
  switch (r.status) {
    case 'PUBLISHED':
      return { label: 'Published', tone: 'success', icon: 'check', advice: 'The post is live.', link: r.postUrl ?? undefined, blocksRetry: true }
    case 'SIMULATED':
      return { label: 'Simulated (development only)', tone: 'warning', icon: 'flask', advice: 'Nothing was posted. LinkedIn publishing is switched off in this environment.', blocksRetry: true }
    case 'FAILED': {
      const x = REASON[r.reason ?? ''] ?? REASON.UNKNOWN_ERROR!
      return { label: x.label, tone: 'danger', icon: 'x', advice: x.advice, blocksRetry: false }
    }
    default:
      return { label: 'Unknown: check LinkedIn first', tone: 'warning', icon: 'alert', advice: 'LinkedIn did not confirm the post. Open LinkedIn and check before you do anything else. Do not publish again: it may already be live.', blocksRetry: true }
  }
}

/** Reconnect is the right next step for an expired connection. */
export const needsReconnect = (r: Pick<PublishResult, 'status' | 'reason'>) => r.status === 'FAILED' && r.reason === 'TOKEN_EXPIRED'
