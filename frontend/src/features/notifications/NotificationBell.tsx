import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { meKey, useMe } from '@/auth/me'
import { timeAgo } from '@/lib/format'
import { Button } from '@/ui/Button'
import { EmptyState } from '@/ui/EmptyState'
import { IconButton } from '@/ui/IconButton'
import { SkeletonRows } from '@/ui/Skeleton'
import { Toggle } from '@/ui/Toggle'
import { useToast } from '@/ui/Toast'

const PAGE = 20

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const qc = useQueryClient()
  const toast = useToast()
  const { data: me } = useMe()
  const list = useInfiniteQuery({
    queryKey: ['notifications', unreadOnly], enabled: open, initialPageParam: 0,
    queryFn: ({ pageParam }) => unwrap(api.GET('/api/notifications', { params: { query: { unread: unreadOnly, page: pageParam, size: PAGE } } })),
    getNextPageParam: (last, all) => (all.length * PAGE < last.total ? all.length : undefined),
  })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ['notifications'] }); void qc.invalidateQueries({ queryKey: meKey }) }
  const read = useMutation({
    mutationFn: (id: number) => unwrap(api.POST('/api/notifications/{id}/read', { params: { path: { id } } })),
    onSuccess: refresh,
  })
  const readAll = useMutation({
    mutationFn: () => unwrap(api.POST('/api/notifications/read-all')),
    onSuccess: (r) => { toast.success(r.updated === 1 ? '1 notification marked as read' : `${r.updated} notifications marked as read`); refresh() },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not mark them as read.'),
  })
  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDoc); document.removeEventListener('keydown', onKey) }
  }, [open])
  // the number comes from the server: the newest answer of the list when it is open, otherwise /api/me
  const unread = list.data?.pages[0]?.unreadCount ?? me?.unreadNotifications ?? 0
  const items = list.data?.pages.flatMap((p) => p.items) ?? []
  return (
    <div className="bell" ref={ref}>
      <IconButton label={unread ? `Notifications, ${unread} unread` : 'Notifications'} badge={unread} aria-expanded={open} aria-haspopup="dialog" onClick={() => setOpen(!open)}>
        <Bell size={20} />
      </IconButton>
      {open ? (
        <div className="bell__panel card" role="dialog" aria-label="Notifications">
          <div className="row bell__head">
            <h2 className="card__title grow">Notifications</h2>
            <Button variant="ghost" icon={<CheckCheck size={18} />} loading={readAll.isPending} disabled={unread === 0} onClick={() => readAll.mutate()}>Mark all as read</Button>
          </div>
          <label className="row bell__filter">
            <Toggle label="Unread only" checked={unreadOnly} onChange={setUnreadOnly} />
            <span>Unread only</span>
          </label>
          {list.isPending ? <SkeletonRows rows={3} label="Loading notifications" /> : list.isError ? (
            <p className="note note--danger" role="alert">Could not load notifications. <Button variant="ghost" onClick={() => { void list.refetch() }}>Try again</Button></p>
          ) : items.length === 0 ? (
            <EmptyState title={unreadOnly ? 'Nothing unread' : 'You are all caught up'}>{unreadOnly ? 'Turn off the filter to see older ones.' : 'New requests and decisions show up here.'}</EmptyState>
          ) : (
            <>
              <ul className="bell__list">
                {items.map((n) => (
                  <li key={n.id}>
                    <button type="button" className="bell__item" data-unread={!n.read} onClick={() => { if (!n.read) read.mutate(n.id) }}>
                      <strong>{n.title}</strong>
                      <span className="muted">{n.body}</span>
                      <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>{timeAgo(n.createdAt)}{n.read ? '' : ' · unread'}</span>
                    </button>
                  </li>
                ))}
              </ul>
              <p className="muted bell__count" aria-live="polite">Showing {items.length} of {list.data?.pages[0]?.total ?? items.length}</p>
              {list.hasNextPage ? <Button block loading={list.isFetchingNextPage} onClick={() => { void list.fetchNextPage() }}>Load more</Button> : null}
            </>
          )}
        </div>
      ) : null}
    </div>
  )
}
