export type Part = { kind: 'text'; text: string } | { kind: 'cite'; n: number; raw: string }

/** Splits an answer into text and [1] style citations. A number with no matching source stays plain text. */
export function splitCitations(text: string, sourceCount: number): Part[] {
  const parts: Part[] = []
  let last = 0
  for (const m of text.matchAll(/\[(\d{1,2})\]/g)) {
    const n = Number(m[1])
    if (n < 1 || n > sourceCount) continue
    if (m.index > last) parts.push({ kind: 'text', text: text.slice(last, m.index) })
    parts.push({ kind: 'cite', n, raw: m[0] })
    last = m.index + m[0].length
  }
  if (last < text.length) parts.push({ kind: 'text', text: text.slice(last) })
  return parts
}

/** The id of the chip that citation n points to. */
export const sourceId = (messageKey: string, n: number) => `src-${messageKey}-${n}`
