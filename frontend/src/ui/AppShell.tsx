import { Menu, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { IconButton } from './IconButton'

export function BrandLogo() {
  return (
    <>
      <img className="brand-logo brand-logo--dark" src="/baracel-logo-transparent.png" alt="Baracel" />
      <img className="brand-logo brand-logo--light" src="/baracel-logo-dark.png" alt="Baracel" />
    </>
  )
}

type Props = { nav: (close: () => void) => ReactNode; footer: ReactNode; children: ReactNode }

/** Sidebar and content. On phones the sidebar becomes a drawer opened from the top bar. */
export function AppShell({ nav, footer, children }: Props) {
  const [open, setOpen] = useState(false)
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])
  return (
    <div className="shell" data-open={open}>
      <header className="topbar">
        <IconButton label={open ? 'Close menu' : 'Open menu'} aria-expanded={open} aria-controls="sidebar" onClick={() => setOpen(!open)}>
          {open ? <X size={20} /> : <Menu size={20} />}
        </IconButton>
        <BrandLogo />
      </header>
      <button className="scrim" aria-label="Close menu" tabIndex={-1} onClick={() => setOpen(false)} />
      <aside className="sidebar" id="sidebar">
        <div className="sidebar__brand"><BrandLogo /></div>
        {nav(() => setOpen(false))}
        <div className="sidebar__spacer" />
        <div className="sidebar__footer">{footer}</div>
      </aside>
      <main className="main" id="main">{children}</main>
    </div>
  )
}
