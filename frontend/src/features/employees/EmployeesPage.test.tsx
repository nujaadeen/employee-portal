import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { meFor, PEOPLE } from '@/test/fixtures'
import { EmployeesPage } from './EmployeesPage'

type Q = { scope?: string }
const person = (name: string, extra = {}) => ({ id: name, name, jobTitle: 'Sales Executive', department: 'Sales', email: `${name.split(' ')[0]!.toLowerCase()}@baracel.example`, ...extra })
const TEAM = ['Aisha Rahman', 'Karim Aziz', 'Nadia Hussein', 'Joel Mathews', 'Hana Saleh'].map((n) => person(n, { status: 'ACTIVE', role: 'SALES', annualLeaveLeft: 10 }))
const ALL_PEOPLE = [...TEAM, person('Omar Haddad', { status: 'ACTIVE', role: 'MANAGER' }), person('Lena Fischer', { status: 'ACTIVE', role: 'HR' })]

vi.mock('@/api/client', () => {
  class ApiError extends Error { status = 0; slug = '' }
  const GET = vi.fn(async (path: string, opts?: { params?: { query?: Q } }) => {
    const scope = opts?.params?.query?.scope
    let data: unknown = []
    if (path === '/api/employees') {
      const items = scope === 'team' ? TEAM : scope === 'all' ? ALL_PEOPLE : ALL_PEOPLE
      data = { items, page: 0, size: 100, total: items.length, statusCounts: scope === 'all' ? { ALL: 7, ACTIVE: 7, ON_LEAVE: 0, ONBOARDING: 0 } : undefined }
    }
    if (path === '/api/employees/{id}') data = { employee: { id: 'x', name: 'Aisha Rahman', code: 'EMP-0142' }, recentLeave: [] }
    return { data, response: { ok: true, status: 200 } }
  })
  return { api: { GET, POST: vi.fn(), PATCH: vi.fn() }, unwrap: async (p: Promise<{ data: unknown }>) => (await p).data, ApiError, pendingApproval: { get: () => false, set: () => {}, subscribe: () => () => {} } }
})

function renderAs(key: keyof typeof PEOPLE) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  qc.setQueryData(['me'], meFor(key))
  return render(<QueryClientProvider client={qc}><MemoryRouter><EmployeesPage /></MemoryRouter></QueryClientProvider>)
}
const tabNames = () => screen.getAllByRole('tab').map((t) => t.textContent?.replace(/\s+\d*$/, '').trim())

describe('Employees page tabs', () => {
  it('Aisha sees only the Directory, no My team and no All employees', async () => {
    renderAs('aisha')
    await screen.findByRole('table', { name: 'Directory' })
    expect(tabNames()).toEqual(['Directory'])
    expect(screen.queryByRole('button', { name: /Invite employee/ })).not.toBeInTheDocument()
  })

  it('Omar lands on My team with exactly the five direct reports and no HR actions', async () => {
    renderAs('omar')
    const table = await screen.findByRole('table', { name: 'My team' })
    expect(tabNames()).toEqual(['My team', 'Directory'])
    const names = within(table).getAllByRole('button', { name: /^Show / }).map((b) => b.getAttribute('aria-label')?.replace('Show ', ''))
    expect(names).toEqual(['Aisha Rahman', 'Karim Aziz', 'Nadia Hussein', 'Joel Mathews', 'Hana Saleh'])
    expect(screen.getByText(/Showing the people who report to you/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Invite employee/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: /All employees/ })).not.toBeInTheDocument()
  })

  it('Lena lands on All employees and can invite', async () => {
    renderAs('lena')
    await screen.findByRole('table', { name: 'All employees' })
    expect(tabNames()).toEqual(['My team', 'Directory', 'All employees'])
    expect(screen.getByRole('button', { name: /Invite employee/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /All employees/ })).toHaveAttribute('aria-selected', 'true')
  })
})
