import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SidebarNav } from '@/ui/SidebarNav'
import { has } from '@/test/fixtures'
import { buildNav } from './nav'

function labels(person: Parameters<typeof has>[0]) {
  return buildNav(has(person)).flatMap((g) => g.items.map((i) => i.label))
}

describe('navigation by permission', () => {
  it('shows Aisha (Sales) no Roles and access', () => {
    expect(labels('aisha')).toEqual(['Dashboard', 'Leave', 'Employees', 'Tasks', 'AI assistant', 'Connected accounts'])
  })
  it('never lists Task detail as a menu item', () => {
    for (const p of ['aisha', 'omar', 'lena', 'daniel'] as const) expect(labels(p)).not.toContain('Task detail')
  })
  it('shows Roles and access only with access:manage', () => {
    expect(labels('omar')).not.toContain('Roles and access')
    expect(labels('lena')).not.toContain('Roles and access')
    expect(labels('daniel')).toContain('Roles and access')
  })
  it('shows Knowledge base only with ai:knowledge:manage, and Connected accounts only with a publish permission', () => {
    expect(labels('daniel')).toEqual(expect.arrayContaining(['Knowledge base', 'Connected accounts']))
    for (const p of ['aisha', 'omar', 'lena'] as const) expect(labels(p)).not.toContain('Knowledge base')
    for (const p of ['omar', 'lena'] as const) expect(labels(p)).not.toContain('Connected accounts')
    expect(labels('aisha')).toContain('Connected accounts')
  })
  it('hides a whole group when none of its items is allowed', () => {
    const nothing = buildNav(() => false)
    expect(nothing.filter((g) => g.items.length > 0).map((g) => g.label)).toEqual(['Workspace'])
  })
  it('renders the groups with their labels in order', () => {
    render(<MemoryRouter><SidebarNav groups={buildNav(has('daniel'))} /></MemoryRouter>)
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getAllByRole('group').map((g) => g.getAttribute('aria-label'))).toEqual(['Workspace', 'People', 'Work', 'Assistant', 'Admin'])
    expect(within(nav).getByRole('link', { name: 'Roles and access' })).toHaveAttribute('href', '/access')
  })
})
