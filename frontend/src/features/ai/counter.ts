/** Platform limits. LinkedIn: at most 3000 characters, and the post reads best when it is short. */
export const LIMITS = { LINKEDIN: { max: 3000, hint: 1300 }, INSTAGRAM: { max: 2200, hint: 1000 }, FACEBOOK: { max: 5000, hint: 800 } } as const
type Platform = keyof typeof LIMITS

export type Counter = {
  count: number
  max: number
  state: 'ok' | 'long' | 'over'
  /** "412 / 3000", or "3012 / 3000 (12 over)" */
  label: string
  /** Shown under the field. Empty while the caption is fine. */
  message: string
}

/** Counts the way the server does (UTF-16 code units, like Java's String.length). */
export function captionCounter(text: string, platform: string = 'LINKEDIN'): Counter {
  const p = (platform in LIMITS ? platform : 'LINKEDIN') as Platform
  const { max, hint } = LIMITS[p]
  const count = text.length
  if (count > max) return { count, max, state: 'over', label: `${count} / ${max} (${count - max} over)`, message: `Too long for ${name(p)}: remove ${count - max} characters to save.` }
  if (count > hint) return { count, max, state: 'long', label: `${count} / ${max}`, message: `Aim for ${hint} characters or fewer: shorter posts are read to the end more often.` }
  return { count, max, state: 'ok', label: `${count} / ${max}`, message: '' }
}

const name = (p: Platform) => p.charAt(0) + p.slice(1).toLowerCase()
