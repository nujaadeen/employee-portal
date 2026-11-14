import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { meFor } from '@/test/fixtures'
import { ToastProvider } from '@/ui/Toast'
import { NewRoleModal } from '@/features/access/NewRoleModal'
import { ProfilePanel } from './ProfilePanel'

const calls: string[] = []
let roleError: { slug: string; message: string } | null = null
let resetFails = false

vi.mock('@/api/client', () => {
  class ApiError extends Error { status: number; slug: string; constructor(s: number, slug: string, m: string) { super(m); this.status = s; this.slug = slug } }
  const GET = vi.fn(async () => ({
    data: { employee: { id: 'e1', name: 'Karim Aziz', email: 'karim.aziz@baracel.example', code: 'EMP-0157', managerId: 'm1' }, recentLeave: [],
      signInMethods: [{ key: 'google', label: 'Google', linked: false, mfa: 'PROVIDER_MANAGED' }, { key: 'password', label: 'Email and password', linked: true, mfa: 'DISABLED' }, { key: 'github', label: 'GitHub', linked: true, mfa: 'PROVIDER_MANAGED' }] },
    response: { ok: true } }))
  const POST = vi.fn(async (path: string, o?: { body?: unknown }) => {
    calls.push(`POST ${path} ${o?.body ? JSON.stringify(o.body) : ''}`.trim())
    if (path === '/api/access/roles' && roleError) throw new ApiError(400, roleError.slug, roleError.message)
    if (path.endsWith('reset-access') && resetFails) throw new ApiError(403, 'forbidden', 'Only an administrator can reset access for an administrator.')
    return { data: path === '/api/access/roles' ? { name: 'CONTRACTOR' } : undefined, response: { ok: true } }
  })
  const unwrap = async (p: Promise<{ data: unknown }>) => (await p).data
  return { api: { GET, POST, DELETE: vi.fn() }, unwrap, ApiError }
})

function wrap(ui: React.ReactNode, who: 'lena' | 'omar' = 'lena') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  qc.setQueryData(['me'], meFor(who))
  return render(<QueryClientProvider client={qc}><ToastProvider><MemoryRouter>{ui}</MemoryRouter></ToastProvider></QueryClientProvider>)
}
beforeEach(() => { calls.length = 0; roleError = null; resetFails = false })

describe('Reset access', () => {
  it('is offered to HR, asks for confirmation that an email will be sent, then calls the server', async () => {
    const user = userEvent.setup()
    wrap(<ProfilePanel id="e1" myId="lena" onEdit={() => {}} />)
    await user.click(await screen.findByRole('button', { name: 'Reset access' }))
    const dialog = await screen.findByRole('dialog', { name: 'Reset access for Karim Aziz?' })
    expect(dialog).toHaveTextContent('An email will be sent to karim.aziz@baracel.example')
    expect(calls).toEqual([])   // nothing is sent before confirming
    await user.click(within(dialog).getByRole('button', { name: 'Send email' }))
    expect(await screen.findByText('Reset email sent')).toBeInTheDocument()
    expect(calls).toEqual(['POST /api/employees/{id}/reset-access'])
  })

  it('shows the server refusal as an error message', async () => {
    resetFails = true
    const user = userEvent.setup()
    wrap(<ProfilePanel id="e1" myId="lena" onEdit={() => {}} />)
    await user.click(await screen.findByRole('button', { name: 'Reset access' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Send email' }))
    expect(await screen.findByText('Only an administrator can reset access for an administrator.')).toBeInTheDocument()
  })

  it('is not offered to a manager, who also does not see sign-in methods', async () => {
    wrap(<ProfilePanel id="e1" myId="omar" onEdit={() => {}} />, 'omar')
    await screen.findByText('EMP-0157')
    expect(screen.queryByRole('button', { name: 'Reset access' })).not.toBeInTheDocument()
  })

  it('shows each sign-in method with MFA text from the enum', async () => {
    wrap(<ProfilePanel id="e1" myId="lena" onEdit={() => {}} />)
    await screen.findByText('Sign-in methods')
    expect(screen.getByText('MFA off')).toBeInTheDocument()
    expect(screen.getAllByText('Managed by provider')).toHaveLength(2)
  })
})

describe('New role', () => {
  function open() { return wrap(<NewRoleModal open onClose={() => {}} onCreated={() => {}} />) }

  it('creates a role with name and description', async () => {
    const user = userEvent.setup()
    open()
    await user.type(screen.getByLabelText('Name'), 'contractor')
    await user.type(screen.getByLabelText('Description (optional)'), 'External')
    await user.click(screen.getByRole('button', { name: 'Create role' }))
    expect(await screen.findByText('Role CONTRACTOR created with no permissions yet')).toBeInTheDocument()
    expect(calls).toEqual(['POST /api/access/roles {"name":"CONTRACTOR","description":"External"}'])   // typed in lower case, sent upper case
  })

  it('shows the server validation error next to the name field', async () => {
    roleError = { slug: 'invalid-role-name', message: 'A role name has 3 to 30 characters: capital letters and underscores only.' }
    const user = userEvent.setup()
    open()
    await user.type(screen.getByLabelText('Name'), 'a1')
    await user.click(screen.getByRole('button', { name: 'Create role' }))
    const field = await screen.findByText(/capital letters and underscores only\.$/)
    expect(field).toHaveAttribute('role', 'alert')
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
  })
})
