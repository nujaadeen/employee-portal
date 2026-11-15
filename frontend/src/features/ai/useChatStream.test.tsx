import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { useChatStream } from './useChatStream'

vi.mock('@/api/client', () => {
  class ApiError extends Error { status = 0; slug = '' }
  return { api: { POST: vi.fn(async () => ({ data: { id: 'c1' }, response: { ok: true } })) }, unwrap: async (p: Promise<{ data: unknown }>) => (await p).data, ApiError, pendingApproval: { set: vi.fn() } }
})
vi.mock('@/auth/oidc', () => ({ getAccessToken: () => 'tok', startLogin: vi.fn() }))
vi.mock('@/env', () => ({ env: { apiUrl: 'http://api.test' } }))

const enc = new TextEncoder()
function sse(parts: string[], opts: { hang?: boolean } = {}) {
  let i = 0
  return new ReadableStream<Uint8Array>({ pull(c) { if (i < parts.length) c.enqueue(enc.encode(parts[i++])); else if (!opts.hang) c.close(); else return new Promise(() => undefined) } })
}
const ev = (name: string, data: unknown) => `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`

function Probe({ onFinished }: { onFinished: (id: string) => void }) {
  const { state, send, stop, retry } = useChatStream(undefined, onFinished)
  return (
    <div>
      <button onClick={() => void send('hello')}>send</button><button onClick={stop}>stop</button><button onClick={retry}>retry</button>
      <output aria-label="status">{state.status}</output><output aria-label="text">{state.text}</output>
      <output aria-label="sources">{state.sources.map((s) => s.title).join('|')}</output>
      <output aria-label="error">{state.error ? `${state.error.kind}:${state.error.waitSeconds ?? ''}` : ''}</output>
    </div>
  )
}
const wrap = (ui: ReactNode) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>

afterEach(() => { vi.unstubAllGlobals() })

describe('useChatStream', () => {
  it('renders tokens as they arrive (split across chunks), sends the bearer token and accepts event-stream', async () => {
    const fetchMock = vi.fn(async () => new Response(sse([ev('token', { text: 'Hold ' }).slice(0, 20), ev('token', { text: 'Hold ' }).slice(20), ev('token', { text: 'the button' }), ev('sources', { sources: [{ title: 'Guide.pdf', collection: 'product' }] }), ev('done', {})]), { status: 200, headers: { 'Content-Type': 'text/event-stream' } }))
    vi.stubGlobal('fetch', fetchMock)
    const finished = vi.fn()
    render(wrap(<Probe onFinished={finished} />))
    await userEvent.click(screen.getByText('send'))
    await waitFor(() => expect(finished).toHaveBeenCalledWith('c1'))
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('http://api.test/api/ai/conversations/c1/messages')
    expect(init.headers).toMatchObject({ Authorization: 'Bearer tok', Accept: 'text/event-stream' })
    expect(JSON.parse(init.body as string)).toEqual({ message: 'hello' })
  })

  it('Stop aborts the request and keeps the partial text', async () => {
    let seenSignal: AbortSignal | undefined
    vi.stubGlobal('fetch', vi.fn(async (_u: string, init: RequestInit) => { seenSignal = init.signal as AbortSignal; return new Response(sse([ev('token', { text: 'Partial ' })], { hang: true }), { status: 200 }) }))
    render(wrap(<Probe onFinished={() => undefined} />))
    await userEvent.click(screen.getByText('send'))
    await waitFor(() => expect(screen.getByLabelText('text')).toHaveTextContent('Partial'))
    expect(screen.getByLabelText('status')).toHaveTextContent('streaming')
    await userEvent.click(screen.getByText('stop'))
    await waitFor(() => expect(screen.getByLabelText('status')).toHaveTextContent('stopped'))
    expect(seenSignal?.aborted).toBe(true)
    expect(screen.getByLabelText('text')).toHaveTextContent('Partial')   // kept
  })

  it('shows the wait time for 429, the unavailable state for 503 and an error event inside the stream', async () => {
    const problem = (status: number, type: string, headers: Record<string, string> = {}) => new Response(JSON.stringify({ type: `https://x/problems/${type}`, detail: 'd' }), { status, headers: { 'Content-Type': 'application/problem+json', ...headers } })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(problem(429, 'rate-limited', { 'Retry-After': '42' })).mockResolvedValueOnce(problem(503, 'ai-unavailable'))
      .mockResolvedValueOnce(new Response(sse([ev('token', { text: 'Half ' }), ev('error', { code: 'ai-unavailable', detail: 'x' })]), { status: 200 })))
    render(wrap(<Probe onFinished={() => undefined} />))
    await userEvent.click(screen.getByText('send'))
    await waitFor(() => expect(screen.getByLabelText('error')).toHaveTextContent('rate-limit:42'))
    await userEvent.click(screen.getByText('retry'))
    await waitFor(() => expect(screen.getByLabelText('error')).toHaveTextContent('unavailable:'))
    await userEvent.click(screen.getByText('retry'))
    await waitFor(() => expect(screen.getByLabelText('error')).toHaveTextContent('unavailable:'))
    expect(screen.getByLabelText('text')).toHaveTextContent('Half')   // the part that arrived stays
    await act(async () => undefined)
  })
})
