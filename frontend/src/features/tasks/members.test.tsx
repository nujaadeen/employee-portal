import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '@/ui/Toast'
import { CreateTaskModal } from './CreateTaskModal'
import { MembersModal } from './MembersModal'

const calls: string[] = []
const MEMBERS = [
  { id: 'p1', name: 'Priya Nair', jobTitle: 'Backend Engineer', initials: 'PN' },
  { id: 'a1', name: 'Aisha Rahman', jobTitle: 'Sales Executive', initials: 'AR' },
]

vi.mock('@/api/client', () => {
  class ApiError extends Error { status = 0; slug = '' }
  const GET = vi.fn(async (path: string) => {
    calls.push(`GET ${path}`)
    if (path === '/api/projects/{key}/members') return { data: MEMBERS, response: { ok: true } }
    if (path === '/api/employees') return { data: { items: [{ id: 'k1', name: 'Karim Aziz' }, { id: 'p1', name: 'Priya Nair' }], total: 2 }, response: { ok: true } }
    return { data: [], response: { ok: true } }
  })
  const POST = vi.fn(async (path: string, o: { body: unknown }) => { calls.push(`POST ${path} ${JSON.stringify(o.body)}`); return { data: MEMBERS, response: { ok: true } } })
  const DELETE = vi.fn(async (path: string) => { calls.push(`DELETE ${path}`); return { data: undefined, response: { ok: true } } })
  return { api: { GET, POST, DELETE, PATCH: vi.fn() }, unwrap: async (p: Promise<{ data: unknown }>) => (await p).data, ApiError }
})

const wrap = (ui: React.ReactNode) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <ToastProvider><MemoryRouter>{ui}</MemoryRouter></ToastProvider>
  </QueryClientProvider>)

beforeEach(() => { calls.length = 0 })

describe('Create task: the assignee picker', () => {
  it('lists the project members, not the whole directory', async () => {
    wrap(<CreateTaskModal open onClose={() => {}} projectKey="BAR" />)
    const picker = await screen.findByLabelText('Assignee')
    await within(picker).findByRole('option', { name: 'Priya Nair' })
    expect(within(picker).getAllByRole('option').map((o) => o.textContent)).toEqual(['Unassigned', 'Priya Nair', 'Aisha Rahman'])
    expect(within(picker).queryByRole('option', { name: 'Karim Aziz' })).not.toBeInTheDocument()
    expect(calls).toContain('GET /api/projects/{key}/members')
    expect(calls).not.toContain('GET /api/employees')
  })
})

describe('Project members', () => {
  it('shows the list but no add or remove actions to a plain member', async () => {
    wrap(<MembersModal open onClose={() => {}} projectKey="BAR" canManage={false} />)
    await screen.findByText('Priya Nair')
    expect(screen.queryByRole('button', { name: /^Remove / })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Add a member')).not.toBeInTheDocument()
    expect(calls).not.toContain('GET /api/employees')
  })

  it('lets a manager add someone who is not yet a member and remove one after confirming', async () => {
    const user = userEvent.setup()
    wrap(<MembersModal open onClose={() => {}} projectKey="BAR" canManage />)
    await screen.findByText('Priya Nair')
    const picker = screen.getByLabelText('Add a member')
    await within(picker).findByRole('option', { name: 'Karim Aziz' })
    expect(within(picker).queryByRole('option', { name: 'Priya Nair' })).not.toBeInTheDocument()   // already a member
    await user.selectOptions(picker, 'k1')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(await screen.findByText('Member added')).toBeInTheDocument()
    expect(calls).toContain('POST /api/projects/{key}/members {"employeeId":"k1"}')

    await user.click(screen.getByRole('button', { name: 'Remove Priya Nair' }))
    const dialog = await screen.findByRole('dialog', { name: 'Remove Priya Nair?' })
    expect(calls.some((c) => c.startsWith('DELETE'))).toBe(false)   // nothing happens before confirming
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }))
    expect(await screen.findByText('Priya Nair removed from BAR')).toBeInTheDocument()
    expect(calls).toContain('DELETE /api/projects/{key}/members/{employeeId}')
  })
})
