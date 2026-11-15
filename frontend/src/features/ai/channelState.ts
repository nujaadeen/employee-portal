import type { Channel } from '@/api/types'

export type ChannelView = {
  usable: boolean
  /** Text shown on the channel, so state is never only a colour. */
  note: string
  /** Show a Connect or Reconnect link to the accounts page. */
  action?: 'connect' | 'reconnect'
}

/** What the Publish to picker shows for one channel. */
export function channelView(c: Pick<Channel, 'provider' | 'status' | 'canPublish'>): ChannelView {
  if (c.provider !== 'LINKEDIN' || c.status === 'COMING_LATER') return { usable: false, note: 'Coming later' }
  switch (c.status) {
    case 'CONNECTED':
    case 'SIMULATED':
      return c.canPublish ? { usable: true, note: c.status === 'SIMULATED' ? 'Simulated' : '' } : { usable: false, note: 'Your role cannot publish here' }
    case 'EXPIRED': return { usable: false, note: 'Connection expired', action: 'reconnect' }
    case 'NOT_AVAILABLE': return { usable: false, note: 'Not enabled yet' }
    default: return { usable: false, note: 'Not connected', action: 'connect' }
  }
}
