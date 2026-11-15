import { sourceId, splitCitations } from './citations'

describe('splitCitations', () => {
  it('turns [1] and [2] into citations that point at sources', () => {
    expect(splitCitations('Hold the button [1] then pair [2].', 2)).toEqual([
      { kind: 'text', text: 'Hold the button ' }, { kind: 'cite', n: 1, raw: '[1]' }, { kind: 'text', text: ' then pair ' }, { kind: 'cite', n: 2, raw: '[2]' }, { kind: 'text', text: '.' },
    ])
  })
  it('leaves a number without a source as plain text', () => {
    expect(splitCitations('See [3] and [0].', 2)).toEqual([{ kind: 'text', text: 'See [3] and [0].' }])
  })
  it('does nothing without sources or without citations', () => {
    expect(splitCitations('Plain', 0)).toEqual([{ kind: 'text', text: 'Plain' }])
    expect(splitCitations('A [1]', 0)).toEqual([{ kind: 'text', text: 'A [1]' }])
    expect(splitCitations('', 3)).toEqual([])
  })
  it('builds the chip id', () => { expect(sourceId('m1', 2)).toBe('src-m1-2') })
})
