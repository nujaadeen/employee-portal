import { captionCounter, LIMITS } from './counter'

describe('captionCounter', () => {
  it('is fine under the hint', () => {
    expect(captionCounter('x'.repeat(412))).toMatchObject({ count: 412, max: 3000, state: 'ok', label: '412 / 3000', message: '' })
  })
  it('hints from 1301 characters on LinkedIn', () => {
    expect(captionCounter('x'.repeat(LIMITS.LINKEDIN.hint)).state).toBe('ok')
    const c = captionCounter('x'.repeat(LIMITS.LINKEDIN.hint + 1))
    expect(c.state).toBe('long')
    expect(c.message).toContain('1300')
  })
  it('allows exactly 3000 and refuses 3001, saying how many to remove', () => {
    expect(captionCounter('x'.repeat(3000)).state).toBe('long')
    const c = captionCounter('x'.repeat(3012))
    expect(c).toMatchObject({ state: 'over', label: '3012 / 3000 (12 over)' })
    expect(c.message).toContain('remove 12 characters')
  })
  it('counts like the server: UTF-16 code units', () => {
    expect(captionCounter('a😀').count).toBe(3)
  })
  it('uses the platform limit and falls back to LinkedIn', () => {
    expect(captionCounter('x'.repeat(2201), 'INSTAGRAM').state).toBe('over')
    expect(captionCounter('x'.repeat(2201), 'SOMETHING').state).toBe('long')
  })
})
