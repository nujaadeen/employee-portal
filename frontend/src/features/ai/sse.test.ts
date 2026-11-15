import { readSse } from './sse'
import type { SseEvent } from './sse'

function streamOf(chunks: string[], opts: { hang?: boolean } = {}): ReadableStream<Uint8Array> {
  const enc = new TextEncoder()
  let i = 0
  return new ReadableStream({
    pull(controller) {
      if (i < chunks.length) controller.enqueue(enc.encode(chunks[i++]))
      else if (!opts.hang) controller.close()
      else return new Promise(() => undefined)   // never ends: waits for an abort
    },
  })
}

async function collect(body: ReadableStream<Uint8Array>, signal?: AbortSignal): Promise<SseEvent[]> {
  const out: SseEvent[] = []
  for await (const e of readSse(body, signal)) out.push(e)
  return out
}

describe('readSse', () => {
  it('parses events in order', async () => {
    const out = await collect(streamOf(['event: token\ndata: {"text":"Hi"}\n\nevent: done\ndata: {}\n\n']))
    expect(out).toEqual([{ event: 'token', data: '{"text":"Hi"}' }, { event: 'done', data: '{}' }])
  })

  it('joins an event that is split across chunks, even in the middle of a word or the blank line', async () => {
    const out = await collect(streamOf(['ev', 'ent: tok', 'en\nda', 'ta: {"text":"Hel', 'lo"}\n', '\nevent: sources\ndata: {"sources":[]}\n\n']))
    expect(out).toEqual([{ event: 'token', data: '{"text":"Hello"}' }, { event: 'sources', data: '{"sources":[]}' }])
  })

  it('handles CRLF, comments, multi-line data and a missing space after the colon', async () => {
    const out = await collect(streamOf([': keep-alive\r\n\r\nevent: token\r\ndata:line one\r\ndata: line two\r\n\r\n']))
    expect(out).toEqual([{ event: 'token', data: 'line one\nline two' }])
  })

  it('uses "message" when no event name is given and emits a final event without a blank line', async () => {
    expect(await collect(streamOf(['data: a\n\ndata: b']))).toEqual([{ event: 'message', data: 'a' }, { event: 'message', data: 'b' }])
  })

  it('delivers an error event like any other so the caller can show it', async () => {
    const out = await collect(streamOf(['event: error\ndata: {"code":"ai-unavailable","detail":"down"}\n\n']))
    expect(out).toEqual([{ event: 'error', data: '{"code":"ai-unavailable","detail":"down"}' }])
  })

  it('does not fail on an empty stream', async () => {
    expect(await collect(streamOf([]))).toEqual([])
  })

  it('stops with an AbortError when the signal aborts, after the events that already arrived', async () => {
    const ctl = new AbortController()
    const seen: SseEvent[] = []
    const body = streamOf(['event: token\ndata: {"text":"Partial "}\n\n'], { hang: true })
    const run = (async () => { for await (const e of readSse(body, ctl.signal)) { seen.push(e); ctl.abort() } })()
    await expect(run).rejects.toMatchObject({ name: 'AbortError' })
    expect(seen).toEqual([{ event: 'token', data: '{"text":"Partial "}' }])
  })

  it('throws at once when the signal is already aborted', async () => {
    const ctl = new AbortController()
    ctl.abort()
    await expect(collect(streamOf(['data: x\n\n']), ctl.signal)).rejects.toMatchObject({ name: 'AbortError' })
  })
})
