import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { meFor } from '@/test/fixtures'
import { ToastProvider } from '@/ui/Toast'
import { NotificationBell } from './NotificationBell'

const calls: string[] = []
const note = (i: number, read = false) => ({ id: i, type: 'T', title: `Note ${i}`, body: 'body', link: '/', read, createdAt: '2026-11-14T10:00:00Z' })
const ALL = Array.from({ length: 25 }, (_, i) => note(i + 1, i >= 3))   // 3 unread, 22 read

vi.mock('@/api/client', () => {
  class ApiError extends Error { status = 0; slug = '' }
  const GET = vi.fn(async (_p: string, o: { params: { query: { unread: boolean; page: number; size: number } } }) => {
    const { unread, page, size } = o.params.query
    calls.push(`GET unread=${unread} page=${page} size=${size}`)
    const pool = unread ? ALL.filter((n) => !n.read) : ALL
    return { data: { items: pool.slice(page * size, page * size + size), total: pool.length, unreadCount: 3, page, size }, response: { ok: true } }
  })
  const POST = vi.fn(async (p: string) => { calls.push(`POST ${p}`); return { data: p.endsWith('read-all') ? { updated: 3 } : {}, response: { ok: true } } })
  return { api: { GET, POST }, unwrap: async (p: Promise<{ data: unknown }>) => (await p).data, ApiError }
})

function setup() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  qc.setQueryData(['me'], { ...meFor('aisha'), unreadNotifications: 3 })
  render(<QueryClientProvider client={qc}><ToastProvider><NotificationBell /></ToastProvider></QueryClientProvider>)
}
beforeEach(() => { calls.length = 0 })

describe('notification bell', () => {
  it('shows the server unread count on the bell before it is opened', () => {
    setup()
    expect(screen.getByRole('button', { name: 'Notifications, 3 unread' })).toBeInTheDocument()
    expect(calls).toEqual([])   // nothing is loaded until it opens
  })

  it('loads the first page, then more with Load more', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: /Notifications/ }))
    const panel = await screen.findByRole('dialog', { name: 'Notifications' })
    expect(await within(panel).findByText('Note 1')).toBeInTheDocument()
    expect(within(panel).getAllByRole('button', { name: /Note \d+/ })).toHaveLength(20)
    expect(within(panel).getByText('Showing 20 of 25')).toBeInTheDocument()
    await user.click(within(panel).getByRole('button', { name: 'Load more' }))
    expect(await within(panel).findByText('Note 25')).toBeInTheDocument()
    expect(within(panel).getByText('Showing 25 of 25')).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
    expect(calls).toEqual(['GET unread=false page=0 size=20', 'GET unread=false page=1 size=20'])
  })

  it('Unread only asks the server for unread ones', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: /Notifications/ }))
    const panel = await screen.findByRole('dialog', { name: 'Notifications' })
    await within(panel).findByText('Note 1')
    await user.click(within(panel).getByRole('switch', { name: 'Unread only' }))
    await within(panel).findByText('Showing 3 of 3')
    expect(within(panel).queryByText('Note 10')).not.toBeInTheDocument()
    expect(calls).toContain('GET unread=true page=0 size=20')
  })

  it('Mark all as read calls the server and confirms with a message', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: /Notifications/ }))
    const panel = await screen.findByRole('dialog', { name: 'Notifications' })
    await user.click(await within(panel).findByRole('button', { name: 'Mark all as read' }))
    expect(await screen.findByText('3 notifications marked as read')).toBeInTheDocument()
    expect(calls).toContain('POST /api/notifications/read-all')
  })
})
