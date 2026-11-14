export type EmployeeTab = 'directory' | 'team' | 'all'

/** Employees tabs by permission: Directory for everyone, My team and All employees only with their permission. */
export function employeeTabs(has: (permission: string) => boolean): { value: EmployeeTab; label: string }[] {
  const tabs: { value: EmployeeTab; label: string }[] = []
  if (has('employee:team')) tabs.push({ value: 'team', label: 'My team' })
  if (has('employee:directory')) tabs.push({ value: 'directory', label: 'Directory' })
  if (has('employee:records')) tabs.push({ value: 'all', label: 'All employees' })
  return tabs
}

/** HR (all records) lands on All employees, a manager on My team, everyone else on the Directory. */
export function defaultTab(has: (permission: string) => boolean): EmployeeTab {
  if (has('employee:records')) return 'all'
  if (has('employee:team')) return 'team'
  return 'directory'
}
