import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'

type Props = { open: boolean; title: string; onClose: () => void; children: ReactNode; actions?: ReactNode; description?: ReactNode }

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** Dialog with a focus trap, Escape to close and focus returned to the opener. One primary button in `actions`. */
export function Modal({ open, title, onClose, children, actions, description }: Props) {
  const titleId = useId()
  const ref = useRef<HTMLDivElement>(null)
  // Callers pass a new onClose on every render. Keeping it in a ref means the effect below runs only when the dialog opens or closes,
  // so focus is moved into the dialog once and typing in a field is never interrupted.
  const closeRef = useRef(onClose)
  useEffect(() => { closeRef.current = onClose })
  useEffect(() => {
    if (!open) return
    const opener = document.activeElement as HTMLElement | null
    const first = ref.current?.querySelector<HTMLElement>(FOCUSABLE)
    first?.focus()
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') { e.stopPropagation(); closeRef.current() }
      if (e.key === 'Tab' && ref.current) {
        const items = Array.from(ref.current.querySelectorAll<HTMLElement>(FOCUSABLE))
        if (!items.length) return
        const a = items[0]!, b = items[items.length - 1]!
        if (e.shiftKey && document.activeElement === a) { e.preventDefault(); b.focus() }
        else if (!e.shiftKey && document.activeElement === b) { e.preventDefault(); a.focus() }
      }
    }
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('keydown', onKey); opener?.focus?.() }
  }, [open])
  if (!open) return null
  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={ref}>
        <h2 id={titleId}>{title}</h2>
        {description ? <p className="muted" style={{ marginBottom: 'var(--space-4)' }}>{description}</p> : null}
        {children}
        {actions ? <div className="modal__actions">{actions}</div> : null}
      </div>
    </div>
  )
}
