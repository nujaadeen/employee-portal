import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import type { AiAccess, PublishResult } from '@/api/types'
import { ToastProvider } from '@/ui/Toast'
import { PublishPanel } from './PublishPanel'
import { useState } from 'react'

const post = vi.hoisted(() => vi.fn())
vi.mock('@/api/client', () => {
  class ApiError extends Error { status = 0; slug = '' }
  return { api: { POST: post }, unwrap: async (p: Promise<{ data: unknown }>) => (await p).data, ApiError }
})

const ch = (code: string, name: string, provider: string, type: string, status: string, canPublish: boolean, extra = {}) => ({ code, name, provider, type, status, canPublish, connected: canPublish, ...extra })
const access = { roles: ['SALES'], collections: [], companyChannels: 1, personalChannels: 0, channels: [
  ch('company-instagram', 'Company Instagram', 'INSTAGRAM', 'COMPANY', 'COMING_LATER', false),
  ch('company-linkedin', 'Company LinkedIn', 'LINKEDIN', 'COMPANY', 'CONNECTED', true, { accountName: 'Baracel' }),
  ch('company-facebook', 'Company Facebook', 'FACEBOOK', 'COMPANY', 'COMING_LATER', false),
  ch('personal-linkedin-x', 'My LinkedIn (personal)', 'LINKEDIN', 'PERSONAL', 'NOT_CONNECTED', false),
] } as unknown as AiAccess
const editor = { draft: { id: 'd1' }, text: 'Headline\n\nCaption text.\n\n#Baracel', dirty: false, valid: true, save: vi.fn() }

function Host({ results0 = [] as PublishResult[] }) {
  const [results, setResults] = useState(results0)
  return <PublishPanel editor={editor as never} suggested={['company-linkedin']} access={access} onEdit={() => undefined} results={results} onResults={setResults} />
}
const setup = (results0?: PublishResult[]) => render(<MemoryRouter><QueryClientProvider client={new QueryClient()}><ToastProvider><Host results0={results0} /></ToastProvider></QueryClientProvider></MemoryRouter>)

describe('PublishPanel', () => {
  beforeEach(() => { post.mockReset() })

  it('lists channels from the API: Instagram and Facebook are disabled with Coming later, a missing connection offers Connect, there is no Schedule', () => {
    setup()
    for (const name of ['Company Instagram', 'Company Facebook']) {
      const b = screen.getByRole('button', { name: new RegExp(name) })
      expect(b).toBeDisabled()
      expect(b).toHaveTextContent('Coming later')
    }
    expect(screen.getByRole('button', { name: /Company LinkedIn/ })).toBeEnabled()
    const personal = screen.getByRole('button', { name: /My LinkedIn/ })
    expect(personal).toBeDisabled()
    expect(personal).toHaveTextContent('Not connected')
    expect(screen.getByRole('link', { name: /^Connect/ })).toHaveAttribute('href', '/accounts')
    expect(screen.queryByRole('button', { name: /Schedule/ })).toBeNull()
  })

  it('asks for confirmation with the exact text and the account, and a double click sends one request', async () => {
    post.mockImplementation(async () => { await new Promise((r) => setTimeout(r, 30)); return { data: [{ channel: 'company-linkedin', status: 'SIMULATED' }], response: { ok: true } } })
    setup()
    await userEvent.click(screen.getByRole('button', { name: /^Publish to 1 channel/ }))
    const dialog = screen.getByRole('dialog', { name: 'Publish this post?' })
    expect(within(dialog).getByLabelText('Post text')).toHaveTextContent('Headline Caption text. #Baracel')
    expect(dialog).toHaveTextContent('Company LinkedIn · Baracel')
    const go = within(dialog).getByRole('button', { name: /^Publish to/ })
    await userEvent.dblClick(go)
    expect(await screen.findByText('Simulated (development only)')).toBeInTheDocument()
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0]![1]).toMatchObject({ params: { path: { id: 'd1' } }, body: { channels: ['company-linkedin'] } })
  })

  it('shows each result with words and the next step: Published with a link, Failed with the reason, Unknown without a retry', () => {
    setup([
      { channel: 'company-linkedin', status: 'PUBLISHED', postUrl: 'https://www.linkedin.com/feed/update/urn:li:share:1/' },
      { channel: 'personal-linkedin-x', status: 'FAILED', reason: 'TOKEN_EXPIRED' },
    ] as PublishResult[])
    expect(screen.getByText('Published')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /View post/ })).toHaveAttribute('href', 'https://www.linkedin.com/feed/update/urn:li:share:1/')
    expect(screen.getByText('Failed: connection expired')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reconnect LinkedIn' })).toHaveAttribute('href', '/accounts')
    expect(screen.getByRole('button', { name: /Company LinkedIn/ })).toBeDisabled()   // already published: cannot be picked again
  })

  it('after Unknown it says to check LinkedIn, offers Copy text and Open LinkedIn, and does not offer to publish that channel again', () => {
    setup([{ channel: 'company-linkedin', status: 'UNKNOWN' }] as PublishResult[])
    expect(screen.getByText('Unknown: check LinkedIn first')).toBeInTheDocument()
    expect(screen.getByText(/Do not publish again/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Copy text' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Open LinkedIn/ })).toHaveAttribute('href', 'https://www.linkedin.com/feed/')
    expect(screen.getByRole('button', { name: /Company LinkedIn/ })).toBeDisabled()
  })
})
