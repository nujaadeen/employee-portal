import { needsReconnect, resultView } from './publishResult'

describe('resultView', () => {
  it('Published has a link and its own words', () => {
    const v = resultView({ status: 'PUBLISHED', postUrl: 'https://www.linkedin.com/feed/update/urn:li:share:1/' })
    expect(v).toMatchObject({ label: 'Published', icon: 'check', link: 'https://www.linkedin.com/feed/update/urn:li:share:1/', blocksRetry: true })
  })
  it('Simulated says it is for development and that nothing was posted', () => {
    const v = resultView({ status: 'SIMULATED' })
    expect(v.label).toContain('development only')
    expect(v.advice).toContain('Nothing was posted')
  })
  it('Failed names the reason and what to do, and may be retried', () => {
    const e = resultView({ status: 'FAILED', reason: 'TOKEN_EXPIRED' })
    expect(e).toMatchObject({ label: 'Failed: connection expired', icon: 'x', blocksRetry: false })
    expect(e.advice).toContain('Reconnect')
    expect(resultView({ status: 'FAILED', reason: 'RATE_LIMITED' }).advice).toContain('Wait')
    expect(resultView({ status: 'FAILED', reason: 'REJECTED' }).advice).toContain('Edit the text')
    expect(resultView({ status: 'FAILED', reason: 'WHATEVER' }).label).toContain('could not publish')
    expect(resultView({ status: 'FAILED' }).blocksRetry).toBe(false)
  })
  it('Unknown tells the person to check LinkedIn and not to publish again', () => {
    const v = resultView({ status: 'UNKNOWN' })
    expect(v.label).toContain('check LinkedIn')
    expect(v.advice).toContain('Do not publish again')
    expect(v.blocksRetry).toBe(true)
    expect(v.icon).toBe('alert')
  })
  it('only an expired token asks for a reconnect', () => {
    expect(needsReconnect({ status: 'FAILED', reason: 'TOKEN_EXPIRED' })).toBe(true)
    expect(needsReconnect({ status: 'FAILED', reason: 'REJECTED' })).toBe(false)
    expect(needsReconnect({ status: 'PUBLISHED' })).toBe(false)
  })
})
