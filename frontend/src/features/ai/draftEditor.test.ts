import { hashtagsError, parseHashtags, postText, tagsToText } from './draftEditor'

describe('draft editing helpers', () => {
  it('reads hashtags separated by spaces or commas, with or without #', () => {
    expect(parseHashtags('#Baracel, QR_Pay  ##Autumn')).toEqual(['Baracel', 'QR_Pay', 'Autumn'])
    expect(parseHashtags('')).toEqual([])
    expect(tagsToText(['#Baracel', 'Autumn'])).toBe('Baracel Autumn')
  })
  it('validates hashtags like the server', () => {
    expect(hashtagsError(['Baracel', 'Ünïcode', 'a_b'])).toBeNull()
    expect(hashtagsError(['two-words'])).toContain('letters, digits')
    expect(hashtagsError(Array.from({ length: 31 }, (_, i) => `t${i}`))).toContain('at most 30')
  })
  it('composes the exact post text', () => {
    expect(postText(' Scan. Pay. Done. ', ' Caption. ', ['A', 'B'])).toBe('Scan. Pay. Done.\n\nCaption.\n\n#A #B')
    expect(postText('', 'Only caption', [])).toBe('Only caption')
  })
})
