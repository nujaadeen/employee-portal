import { has } from '@/test/fixtures'
import { defaultTab, employeeTabs } from './tabs'

describe('employee tabs by permission', () => {
  it('gives Aisha only the Directory', () => {
    expect(employeeTabs(has('aisha')).map((t) => t.label)).toEqual(['Directory'])
    expect(defaultTab(has('aisha'))).toBe('directory')
  })
  it('gives Omar My team (the default) and the Directory, but not All employees', () => {
    expect(employeeTabs(has('omar')).map((t) => t.label)).toEqual(['My team', 'Directory'])
    expect(defaultTab(has('omar'))).toBe('team')
  })
  it('gives Lena All employees as the default, with My team and the Directory', () => {
    expect(employeeTabs(has('lena')).map((t) => t.label)).toEqual(['My team', 'Directory', 'All employees'])
    expect(defaultTab(has('lena'))).toBe('all')
  })
})
