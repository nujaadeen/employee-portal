const TONES = ['info', 'success', 'warning', 'danger'] as const

export function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1]![0] : '')).toUpperCase()
}

function toneFor(name: string) {
  let h = 0
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return TONES[h % TONES.length]!
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  return <span aria-hidden="true" className={`avatar avatar--${toneFor(name)}${size === 'md' ? '' : ` avatar--${size}`}`}>{initialsOf(name)}</span>
}
