import { CircleAlert, CircleCheck, X } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

type Tone = 'success' | 'danger'
type Toast = { id: number; tone: Tone; message: string }
type Api = { success: (m: string) => void; error: (m: string) => void }

const Ctx = createContext<Api>({ success: () => {}, error: () => {} })

/** Short messages after an action. Errors stay until closed; successes fade after a few seconds. Announced to screen readers. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([])
  const next = useRef(1)
  const remove = useCallback((id: number) => setItems((l) => l.filter((t) => t.id !== id)), [])
  const push = useCallback((tone: Tone, message: string) => {
    const id = next.current++
    setItems((l) => [...l, { id, tone, message }])
    if (tone === 'success') setTimeout(() => remove(id), 5000)
  }, [remove])
  const api = useMemo<Api>(() => ({ success: (m) => push('success', m), error: (m) => push('danger', m) }), [push])
  return (
    <Ctx.Provider value={api}>
      {children}
      <div className="toasts" role="region" aria-label="Notifications from the last action">
        {items.map((t) => (
          <div key={t.id} className={`toast toast--${t.tone}`} role={t.tone === 'danger' ? 'alert' : 'status'}>
            {t.tone === 'success' ? <CircleCheck size={20} aria-hidden="true" /> : <CircleAlert size={20} aria-hidden="true" />}
            <span className="grow">{t.message}</span>
            <button type="button" className="toast__close" aria-label="Dismiss message" onClick={() => remove(t.id)}><X size={16} /></button>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  )
}

export const useToast = () => useContext(Ctx)
