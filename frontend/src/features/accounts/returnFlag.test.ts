import { returnMessage } from './returnFlag'
import { daysUntil, whenLabel } from '@/lib/format'

const q = (s: string) => new URLSearchParams(s)

describe('returnMessage', () => {
  it('says nothing without a flag', () => { expect(returnMessage(q(''))).toBeNull() })
  it('success names what was connected', () => {
    expect(returnMessage(q('linkedin=connected&target=personal'))).toEqual({ tone: 'success', text: 'Your LinkedIn account is connected.' })
    expect(returnMessage(q('linkedin=connected&target=company'))?.text).toContain('Company LinkedIn page')
  })
  it('cancel is information, not an error', () => { expect(returnMessage(q('linkedin=cancelled'))?.tone).toBe('info') })
  it('errors explain the reason', () => {
    expect(returnMessage(q('linkedin=error&reason=invalid-state'))).toMatchObject({ tone: 'danger', text: expect.stringContaining('expired or was already used') })
    expect(returnMessage(q('linkedin=error&reason=no-organization'))?.text).toContain('company page')
    expect(returnMessage(q('linkedin=error&reason=weird'))?.text).toContain('did not work')
  })
})

describe('time labels', () => {
  const now = new Date(2026, 10, 15, 16, 0)
  it('whenLabel', () => {
    expect(whenLabel(new Date(2026, 10, 15, 14, 31).toISOString(), now)).toBe('Today 14:31')
    expect(whenLabel(new Date(2026, 10, 14, 17, 5).toISOString(), now)).toBe('Yesterday 17:05')
    expect(whenLabel(new Date(2026, 10, 9, 11, 20).toISOString(), now)).toBe('9 Nov 11:20')
  })
  it('daysUntil', () => {
    expect(daysUntil(new Date(2027, 0, 6, 16, 0).toISOString(), now)).toBe('52 days')
    expect(daysUntil(new Date(2026, 10, 16, 10, 0).toISOString(), now)).toBe('1 day')
    expect(daysUntil(new Date(2026, 10, 10).toISOString(), now)).toBe('expired')
    expect(daysUntil(null, now)).toBeNull()
  })
})
