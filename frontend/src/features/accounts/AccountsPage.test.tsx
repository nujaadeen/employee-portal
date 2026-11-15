import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '@/ui/Toast'
import { AccountsPage } from './AccountsPage'

const perms = vi.hoisted(() => ({ set: new Set<string>() }))
vi.mock('@/auth/me', () => ({ useMe: () => ({ data: undefined }), usePermission: (p: string) => perms.set.has(p), usePermissions: () => ({ has: (p: string) => perms.set.has(p) }) }))
const calls = vi.hoisted(() => [] as string[])
vi.mock('@/api/client', () => {
  class ApiError extends Error { status = 0; slug = '' }
  const GET = vi.fn(async (path: string) => {
    if (path === '/api/social/connections') return { data: [
      { id: 'cc', provider: 'LINKEDIN', type: 'COMPANY', displayName: 'Baracel', status: 'CONNECTED', expiresAt: null, profileUrl: 'https://www.linkedin.com/company/1', canDisconnect: true, canReconnect: true },
      { id: 'pc', provider: 'LINKEDIN', type: 'PERSONAL', displayName: 'Aisha Rahman', status: 'CONNECTED', expiresAt: '2099-01-01T00:00:00Z', profileUrl: null, canDisconnect: true, canReconnect: true },
    ], response: { ok: true } }
    if (path === '/api/ai/access') return { data: { roles: [], collections: [], companyChannels: 1, personalChannels: 1, channels: [
      { code: 'company-linkedin', name: 'Company LinkedIn', provider: 'LINKEDIN', type: 'COMPANY', status: 'CONNECTED', canPublish: true, connected: true },
      { code: 'personal', name: 'My LinkedIn', provider: 'LINKEDIN', type: 'PERSONAL', status: 'CONNECTED', canPublish: true, connected: true },
    ] }, response: { ok: true } }
    return { data: { items: [{ id: 1, createdAt: '2026-11-15T10:00:00Z', channel: 'company-linkedin', channelName: 'Company LinkedIn', headline: 'Scan. Pay. Done.', status: 'FAILED', reason: 'TOKEN_EXPIRED', message: null, postUrl: null }], page: 0, size: 10, total: 1 }, response: { ok: true } }
  })
  const DELETE = vi.fn(async (p: string) => { calls.push(`DELETE ${p}`); return { data: undefined, response: { ok: true } } })
  return { api: { GET, DELETE, POST: vi.fn() }, unwrap: async (p: Promise<{ data: unknown }>) => (await p).data, ApiError }
})

function setup() {
  return render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ToastProvider><AccountsPage /></ToastProvider></QueryClientProvider></MemoryRouter>)
}

describe('Connected accounts', () => {
  beforeEach(() => { perms.set = new Set(); calls.length = 0 })

  it('shows Instagram and Facebook as Coming later and company actions only to people with access:manage', async () => {
    perms.set = new Set(['social:publish:company', 'social:publish:personal'])   // Aisha: may publish, cannot manage
    setup()
    const company = (await screen.findByRole('heading', { name: 'Company LinkedIn page' })).closest('section')!
    expect(within(company).queryByRole('button', { name: /Reconnect|Disconnect|Connect/ })).toBeNull()
    expect(within(company).getByRole('link', { name: /View page/ })).toHaveAttribute('href', 'https://www.linkedin.com/company/1')
    const mine = screen.getByRole('heading', { name: 'My LinkedIn' }).closest('section')!
    expect(within(mine).getByRole('button', { name: 'Reconnect' })).toBeInTheDocument()
    expect(within(mine).getByRole('button', { name: 'Disconnect' })).toBeInTheDocument()
    for (const n of ['Instagram', 'Facebook']) expect(within(screen.getByRole('heading', { name: n }).closest('section')!).getByText('Coming later')).toBeInTheDocument()
  })

  it('an administrator can disconnect the company page, after a confirmation', async () => {
    perms.set = new Set(['social:publish:company', 'access:manage'])
    setup()
    const company = (await screen.findByRole('heading', { name: 'Company LinkedIn page' })).closest('section')!
    await userEvent.click(within(company).getByRole('button', { name: 'Disconnect' }))
    const dialog = screen.getByRole('dialog', { name: 'Disconnect Company LinkedIn page?' })
    expect(calls).toEqual([])   // nothing yet
    await userEvent.click(within(dialog).getByRole('button', { name: 'Disconnect' }))
    expect(calls).toEqual(['DELETE /api/social/connections/{id}'])
    expect(screen.queryByRole('heading', { name: 'My LinkedIn' })).toBeNull()   // no personal permission: no personal card
  })

  it('the history shows the result as words with a Reconnect link for an expired connection', async () => {
    perms.set = new Set(['social:publish:personal'])
    setup()
    expect(await screen.findByText('Failed: connection expired')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reconnect' })).toBeInTheDocument()
    expect(screen.getByText('Scan. Pay. Done.')).toBeInTheDocument()
  })
})
