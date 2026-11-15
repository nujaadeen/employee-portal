import { chatError, fromStreamEvent } from './chatError'

describe('chatError', () => {
  it('429 shows the wait time from Retry-After', () => {
    const e = chatError(429, 'rate-limited', '17')
    expect(e).toMatchObject({ kind: 'rate-limit', waitSeconds: 17 })
    expect(e.message).toContain('17 seconds')
  })
  it('429 without a usable header still gives a wait', () => {
    expect(chatError(429, 'rate-limited', 'soon').waitSeconds).toBe(30)
  })
  it('ai-unavailable (503) is the Assistant unavailable state', () => {
    expect(chatError(503, 'ai-unavailable').kind).toBe('unavailable')
    expect(fromStreamEvent('ai-unavailable', 'x').kind).toBe('unavailable')
  })
  it('pending approval has its own message', () => {
    const e = chatError(403, 'pending-approval')
    expect(e.kind).toBe('pending')
    expect(e.message).toContain('waiting for HR approval')
  })
  it('anything else keeps the server detail', () => {
    expect(chatError(502, 'invalid-draft', null, 'The model did not return a valid draft')).toMatchObject({ kind: 'failed', message: 'The model did not return a valid draft' })
    expect(fromStreamEvent('invalid-draft', 'bad').message).toBe('bad')
  })
})
