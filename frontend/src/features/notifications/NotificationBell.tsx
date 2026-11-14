import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { api, unwrap } from '@/api/client'
import { meKey, useMe } from '@/auth/me'
import { EmptyState } from '@/ui/EmptyState'
import { IconButton } from '@/ui/IconButton'
import { SkeletonRows } from '@/ui/Skeleton'

function ago(iso: string) {
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  if (mins < 60) return `${mins} min ago`
  if (mins < 60 * 24) return `${Math.round(mins / 60)} h ago`
  return `${Math.round(mins / 1440)} d ago`
}

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const qc = useQueryClient()
  const { data: me } = useMe()
  const list = useQuery({ queryKey: ['notifications'], queryFn: () => unwrap(api.GET('/api/notifications')), enabled: open })
  const read = useMutation({
    mutationFn: (id: number) => unwrap(api.POST('/api/notifications/{id}/read', { params: { path: { id } } })),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['notifications'] }); void qc.invalidateQueries({ queryKey: meKey }) },
  })
  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDoc); document.removeEventListener('keydown', onKey) }
  }, [open])
  const unread = me?.unreadNotifications ?? 0
  return (
    <div className="bell" ref={ref}>
      <IconButton label={unread ? `Notifications, ${unread} unread` : 'Notifications'} badge={unread} aria-expanded={open} aria-haspopup="dialog" onClick={() => setOpen(!open)}>
        <Bell size={20} />
      </IconButton>
      {open ? (
        <div className="bell__panel card" role="dialog" aria-label="Notifications">
          <h2 className="card__title" style={{ marginBottom: 'var(--space-3)' }}>Notifications</h2>
          {list.isPending ? <SkeletonRows rows={3} label="Loading notifications" /> : list.isError ? (
            <p className="note note--danger" role="alert">Could not load notifications.</p>
          ) : list.data.length === 0 ? (
            <EmptyState title="You are all caught up">New requests and decisions show up here.</EmptyState>
          ) : (
            <ul className="bell__list">
              {list.data.map((n) => (
                <li key={n.id}>
                  <button type="button" className="bell__item" data-unread={!n.read} onClick={() => { if (!n.read) read.mutate(n.id!) }}>
                    <strong>{n.title}</strong>
                    <span className="muted">{n.body}</span>
                    <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>{ago(n.createdAt ?? '')}{n.read ? '' : ' · unread'}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  )
}
