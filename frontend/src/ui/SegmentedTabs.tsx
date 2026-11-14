import { useRef } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'

export type TabItem<T extends string> = { value: T; label: ReactNode }

type Props<T extends string> = { tabs: TabItem<T>[]; value: T; onChange: (v: T) => void; label: string }

/** Pill tabs. Left and right arrow keys move between tabs. */
export function SegmentedTabs<T extends string>({ tabs, value, onChange, label }: Props<T>) {
  const ref = useRef<HTMLDivElement>(null)
  function onKey(e: KeyboardEvent) {
    if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return
    const i = tabs.findIndex((t) => t.value === value)
    const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length]
    if (next) {
      onChange(next.value)
      requestAnimationFrame(() => ref.current?.querySelector<HTMLElement>('[aria-selected="true"]')?.focus())
    }
  }
  return (
    <div className="tabs" role="tablist" aria-label={label} ref={ref} onKeyDown={onKey}>
      {tabs.map((t) => (
        <button key={t.value} type="button" role="tab" className="tab" aria-selected={t.value === value} tabIndex={t.value === value ? 0 : -1} onClick={() => onChange(t.value)}>
          {t.label}
        </button>
      ))}
    </div>
  )
}
