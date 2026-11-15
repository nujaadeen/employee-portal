/** One server-sent event. */
export type SseEvent = { event: string; data: string }

/**
 * Reads a Server-Sent Events body. EventSource cannot send the bearer token, so the chat uses fetch and this parser.
 * Handles events split across chunks, CRLF line ends, comments and multi-line data. When the signal aborts the reader is
 * cancelled and an AbortError is thrown, so the caller can keep the partial text.
 */
export async function* readSse(body: ReadableStream<Uint8Array>, signal?: AbortSignal): AsyncGenerator<SseEvent> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  const onAbort = () => { void reader.cancel().catch(() => undefined) }
  signal?.addEventListener('abort', onAbort)
  let buffer = ''
  try {
    for (;;) {
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      const { value, done } = await reader.read()
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n?/g, '\n')
      let cut: number
      while ((cut = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, cut)
        buffer = buffer.slice(cut + 2)
        const ev = parseBlock(block)
        if (ev) yield ev
      }
    }
    buffer += decoder.decode().replace(/\r\n?/g, '\n')
    const last = parseBlock(buffer.trim() === '' ? '' : buffer)   // a final event without the blank line
    if (last) yield last
  } finally {
    signal?.removeEventListener('abort', onAbort)
    reader.releaseLock?.()
  }
}

function parseBlock(block: string): SseEvent | null {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue   // blank or comment (keep-alive)
    const i = line.indexOf(':')
    const field = i < 0 ? line : line.slice(0, i)
    let value = i < 0 ? '' : line.slice(i + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }
  return data.length || event !== 'message' ? { event, data: data.join('\n') } : null
}
