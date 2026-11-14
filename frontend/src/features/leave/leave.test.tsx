import { render, screen, within } from '@testing-library/react'
import { dateRange, plural } from '@/lib/format'
import { entriesForDay, monthGrid, weekOf } from './calendar'
import { PreviewSummary } from './PreviewSummary'
import { fieldFor } from './RequestForm'

describe('leave request summary', () => {
  it('shows working days, balance after and overlaps', () => {
    render(<PreviewSummary preview={{ workingDays: 1, balanceAfter: 13, overlaps: [], overlapsOwnRequest: false }} department="Sales" />)
    const list = screen.getByLabelText('Request summary')
    expect(within(list).getByText('Working days').nextSibling).toHaveTextContent('1')
    expect(within(list).getByText('Balance after').nextSibling).toHaveTextContent('13 days')
    expect(within(list).getByText('Overlaps').nextSibling).toHaveTextContent('Nobody on Sales')
  })
  it('names the people who are also away', () => {
    render(<PreviewSummary preview={{ workingDays: 5, balanceAfter: 9, overlaps: ['Karim Aziz', 'Joel Mathews'], overlapsOwnRequest: false }} department="Sales" />)
    expect(screen.getByText('Karim Aziz, Joel Mathews')).toBeInTheDocument()
  })
  it('uses the singular for one day and "No limit" for unpaid leave', () => {
    const { rerender } = render(<PreviewSummary preview={{ workingDays: 1, balanceAfter: 1, overlaps: [], overlapsOwnRequest: false }} />)
    expect(screen.getByText('1 day')).toBeInTheDocument()
    rerender(<PreviewSummary preview={{ workingDays: 3, overlaps: [], overlapsOwnRequest: false }} />)
    expect(screen.getByText('No limit')).toBeInTheDocument()
  })
  it('shows dashes until the server has answered', () => {
    render(<PreviewSummary />)
    expect(screen.getAllByText('–')).toHaveLength(3)
  })
})

describe('server errors land next to the right field', () => {
  it('maps problem types to fields', () => {
    expect(fieldFor('insufficient-balance')).toBe('type')
    expect(fieldFor('leave-overlap')).toBe('from')
    expect(fieldFor('invalid-dates')).toBe('to')
    expect(fieldFor('no-working-days')).toBe('to')
    expect(fieldFor('something-else')).toBe('form')
  })
})

describe('calendar helpers', () => {
  it('builds November 2026 with weeks starting on Monday', () => {
    const weeks = monthGrid(2026, 10)
    expect(weeks).toHaveLength(6)   // like the screen: an empty first row and 30 Nov on its own row
    expect(weeks[0]!.filter((c) => c.inMonth).map((c) => c.day)).toEqual([1])   // 1 Nov 2026 is a Sunday
    expect(weeks[5]![0]).toMatchObject({ iso: '2026-11-30', inMonth: true })
  })
  it('shows leave on weekdays only', () => {
    const entries = [{ from: '2026-11-13', to: '2026-11-16' }]
    expect(entriesForDay(entries, '2026-11-13')).toHaveLength(1)
    expect(entriesForDay(entries, '2026-11-14')).toHaveLength(0)   // Saturday
    expect(entriesForDay(entries, '2026-11-16')).toHaveLength(1)
  })
  it('lists the seven days of a week', () => {
    expect(weekOf('2026-11-13').map((c) => c.day)).toEqual([9, 10, 11, 12, 13, 14, 15])
  })
  it('formats ranges like the screen', () => {
    expect(dateRange('2026-11-23', '2026-11-27')).toBe('23 to 27 Nov')
    expect(dateRange('2026-11-30', '2026-11-30')).toBe('30 Nov')
    expect(plural(1, 'day')).toBe('1 day')
    expect(plural(5, 'day')).toBe('5 days')
  })
})
