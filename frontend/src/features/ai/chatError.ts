import { ApiError } from '@/api/client'

export type ChatErrorKind = 'unavailable' | 'rate-limit' | 'pending' | 'failed'
export type ChatError = { kind: ChatErrorKind; message: string; waitSeconds?: number }

/** Maps a failed chat request (or an error event) to what the person should see. */
export function chatError(status: number, slug: string, retryAfter?: string | null, detail?: string): ChatError {
  if (status === 403 && slug === 'pending-approval') return { kind: 'pending', message: 'Your account is waiting for HR approval. The assistant is available once you are approved.' }
  if (status === 429) {
    const wait = Math.max(1, Number.parseInt(retryAfter ?? '', 10) || 30)
    return { kind: 'rate-limit', waitSeconds: wait, message: `You are sending messages too fast. You can send 20 messages a minute. Try again in ${wait} seconds.` }
  }
  if (status === 503 || slug === 'ai-unavailable') return { kind: 'unavailable', message: 'The assistant is unavailable right now. Nothing was lost: try again in a minute.' }
  return { kind: 'failed', message: detail?.trim() ? detail : 'The assistant could not answer.' }
}

export function fromApiError(e: unknown): ChatError {
  if (e instanceof ApiError) return chatError(e.status, e.slug, undefined, e.message)
  return chatError(0, '', undefined, undefined)
}

/** An error event inside the stream ({code, detail}). */
export function fromStreamEvent(code: string, detail: string): ChatError {
  if (code === 'ai-unavailable') return chatError(503, code)
  return chatError(0, code, undefined, detail)
}
