import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useReducer, useRef } from 'react'
import { api, ApiError, pendingApproval, unwrap } from '@/api/client'
import type { ChatSource, Draft } from '@/api/types'
import { getAccessToken, startLogin } from '@/auth/oidc'
import { env } from '@/env'
import { chatError, fromApiError, fromStreamEvent } from './chatError'
import type { ChatError } from './chatError'
import { readSse } from './sse'

export type StreamState = {
  /** idle, streaming, stopped (the person pressed Stop) or error. */
  status: 'idle' | 'streaming' | 'stopped' | 'error'
  question: string | null
  text: string
  sources: ChatSource[]
  draft: Draft | null
  error: ChatError | null
}

const IDLE: StreamState = { status: 'idle', question: null, text: '', sources: [], draft: null, error: null }

type Action =
  | { type: 'start'; question: string }
  | { type: 'token'; text: string }
  | { type: 'sources'; sources: ChatSource[] }
  | { type: 'draft'; draft: Draft }
  | { type: 'stopped' }
  | { type: 'error'; error: ChatError }
  | { type: 'reset' }

function reduce(s: StreamState, a: Action): StreamState {
  switch (a.type) {
    case 'start': return { ...IDLE, status: 'streaming', question: a.question }
    case 'token': return { ...s, text: s.text + a.text }
    case 'sources': return { ...s, sources: a.sources }
    case 'draft': return { ...s, draft: a.draft }
    case 'stopped': return { ...s, status: 'stopped' }
    case 'error': return { ...s, status: 'error', error: a.error }
    case 'reset': return IDLE
  }
}

/**
 * Sends a message and reads the answer as server-sent events with fetch (EventSource cannot send the bearer token).
 * Stop aborts the request and keeps the partial text; the server saves it as a STOPPED answer.
 */
export function useChatStream(conversationId: string | undefined, onFinished: (conversationId: string) => void) {
  const qc = useQueryClient()
  const [state, dispatch] = useReducer(reduce, IDLE)
  const abort = useRef<AbortController | null>(null)
  const stoppedByUser = useRef(false)
  const lastQuestion = useRef('')

  // leaving the page cancels the request
  useEffect(() => () => abort.current?.abort(), [])

  const settle = useCallback(async (cid: string) => {
    await qc.invalidateQueries({ queryKey: ['ai'] })
    dispatch({ type: 'reset' })
    onFinished(cid)
  }, [qc, onFinished])

  const send = useCallback(async (message: string) => {
    lastQuestion.current = message
    stoppedByUser.current = false
    dispatch({ type: 'start', question: message })
    const ctl = new AbortController()
    abort.current = ctl
    let cid = conversationId
    try {
      if (!cid) cid = (await unwrap(api.POST('/api/ai/conversations', { body: {} }))).id
      const token = getAccessToken()
      const res = await fetch(`${env.apiUrl}/api/ai/conversations/${cid}/messages`, {
        method: 'POST',
        signal: ctl.signal,
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify({ message }),
      })
      if (!res.ok || !res.body) {
        if (res.status === 401) void startLogin()
        const problem = (await res.json().catch(() => ({}))) as { type?: string; detail?: string }
        const slug = problem.type?.split('/').pop() ?? ''
        if (res.status === 403 && slug === 'pending-approval') pendingApproval.set(true)
        dispatch({ type: 'error', error: chatError(res.status, slug, res.headers.get('Retry-After'), problem.detail) })
        return
      }
      for await (const ev of readSse(res.body, ctl.signal)) {
        const data = ev.data ? (JSON.parse(ev.data) as Record<string, unknown>) : {}
        if (ev.event === 'token') dispatch({ type: 'token', text: String(data.text ?? '') })
        else if (ev.event === 'sources') dispatch({ type: 'sources', sources: (data.sources ?? []) as ChatSource[] })
        else if (ev.event === 'draft') dispatch({ type: 'draft', draft: data.draft as Draft })
        else if (ev.event === 'error') { dispatch({ type: 'error', error: fromStreamEvent(String(data.code ?? ''), String(data.detail ?? '')) }); void qc.invalidateQueries({ queryKey: ['ai'] }); return }
        else if (ev.event === 'done') { await settle(cid); return }
      }
      // the stream ended without a done event
      dispatch({ type: 'error', error: chatError(0, 'stream-ended', null, 'The answer ended early.') })
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        if (stoppedByUser.current) {
          dispatch({ type: 'stopped' })
          // the server saves what had arrived as a STOPPED answer; show it from there
          setTimeout(() => { if (cid) void settle(cid) }, 700)
        }
        return
      }
      dispatch({ type: 'error', error: e instanceof ApiError ? fromApiError(e) : chatError(503, 'ai-unavailable') })
    }
  }, [conversationId, qc, settle])

  const stop = useCallback(() => { stoppedByUser.current = true; abort.current?.abort() }, [])
  const retry = useCallback(() => { if (lastQuestion.current) void send(lastQuestion.current) }, [send])
  const reset = useCallback(() => dispatch({ type: 'reset' }), [])

  return { state, send, stop, retry, reset }
}
