import { channelView } from './channelState'

describe('channelView', () => {
  it('Instagram and Facebook are disabled and say Coming later', () => {
    expect(channelView({ provider: 'INSTAGRAM', status: 'COMING_LATER', canPublish: false })).toEqual({ usable: false, note: 'Coming later' })
    expect(channelView({ provider: 'FACEBOOK', status: 'COMING_LATER', canPublish: false }).usable).toBe(false)
  })
  it('a connected LinkedIn channel is usable only with the permission', () => {
    expect(channelView({ provider: 'LINKEDIN', status: 'CONNECTED', canPublish: true })).toEqual({ usable: true, note: '' })
    expect(channelView({ provider: 'LINKEDIN', status: 'CONNECTED', canPublish: false })).toMatchObject({ usable: false, note: 'Your role cannot publish here' })
  })
  it('simulated LinkedIn (development) is usable and labelled', () => {
    expect(channelView({ provider: 'LINKEDIN', status: 'SIMULATED', canPublish: true })).toEqual({ usable: true, note: 'Simulated' })
  })
  it('not connected offers Connect, expired offers Reconnect', () => {
    expect(channelView({ provider: 'LINKEDIN', status: 'NOT_CONNECTED', canPublish: false })).toMatchObject({ usable: false, action: 'connect' })
    expect(channelView({ provider: 'LINKEDIN', status: 'EXPIRED', canPublish: false })).toMatchObject({ usable: false, action: 'reconnect' })
  })
  it('the company page before it is enabled', () => {
    expect(channelView({ provider: 'LINKEDIN', status: 'NOT_AVAILABLE', canPublish: false })).toMatchObject({ usable: false, note: 'Not enabled yet' })
  })
})
