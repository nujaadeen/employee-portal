import { expect, test } from '@playwright/test'
import { asUser, shot } from './helpers'

// Runs against the AI service with AI_LLM_PROVIDER=fake (deterministic answers that cite the retrieved sources), see docs/run.md.

test.describe('AI assistant as Aisha Rahman (SALES)', () => {
  test('streams an answer with sources only from her collections, links [1] to its chip, and takes feedback', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'aisha.rahman', '/ai')
    const request = page.waitForRequest((r) => r.url().includes('/messages') && r.method() === 'POST')
    const response = page.waitForResponse((r) => r.url().includes('/messages') && r.request().method() === 'POST')
    await page.getByRole('textbox', { name: 'Message' }).fill('How do I pair the Lantern Pro with my phone?')
    await page.getByRole('button', { name: 'Send message' }).click()
    // the answer is read as server-sent events with the bearer token (fetch, not EventSource)
    const req = await request
    expect(req.headers()['accept']).toContain('text/event-stream')
    expect(req.headers()['authorization']).toMatch(/^Bearer /)
    expect((await response).headers()['content-type']).toContain('text/event-stream')

    const log = page.getByRole('log', { name: 'Conversation' })
    await expect(log.getByText(/Here is what the sources say/)).toBeVisible()
    const chips = log.locator('.source')
    await expect(chips.first()).toBeVisible()
    const where = await chips.evaluateAll((els) => els.map((e) => e.getAttribute('title') ?? ''))
    expect(where.length).toBeGreaterThan(0)
    for (const w of where) expect(w).toMatch(/^From (product|brand|campaign)\b/)   // never engineering or hr-policies

    // [1] links to the matching chip
    await log.getByRole('link', { name: /^Source 1:/ }).click()
    await expect(chips.first()).toBeFocused()

    // thumbs up with a comment
    await page.getByRole('button', { name: 'Helpful', exact: true }).click()
    await page.getByLabel('Add a comment (optional)').fill('Clear answer')
    await page.getByRole('button', { name: 'Send feedback' }).click()
    await expect(page.getByText('Thanks for the feedback.')).toBeVisible()
    await shot(page, '08-ai-answer-aisha')
    await context.close()
  })

  test('edits the draft, publishes with LinkedIn switched off and sees Simulated; Instagram is Coming later', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'aisha.rahman', '/ai')
    await page.getByRole('textbox', { name: 'Message' }).fill('Write a LinkedIn post about the Lantern Pro')
    await page.getByRole('button', { name: 'Send message' }).click()
    const draft = page.getByRole('article', { name: 'Draft post' })
    await expect(draft).toBeVisible()

    // edit: the counter follows the caption and the limit is 3000
    const caption = draft.getByLabel('Caption')
    await caption.fill('The Lantern Pro lasts a full weekend on one charge.')
    await expect(draft.getByText('51 / 3000')).toBeVisible()
    await caption.fill('x'.repeat(3010))
    await expect(draft.getByText('3010 / 3000 (10 over)')).toBeVisible()
    await expect(draft.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    await caption.fill('The Lantern Pro lasts a full weekend on one charge.')
    await draft.getByLabel('Headline').fill('Light up the long evenings')
    await draft.getByRole('button', { name: 'Save changes' }).click()
    await expect(draft.getByText('Saved')).toBeVisible()

    // the picker: Instagram and Facebook say Coming later, there is no Schedule button
    const publish = page.getByRole('region', { name: 'Publish this draft' })
    for (const name of ['Company Instagram', 'Company Facebook']) {
      const b = publish.getByRole('button', { name: new RegExp(name) })
      await expect(b).toBeDisabled()
      await expect(b).toContainText('Coming later')
    }
    await expect(page.getByRole('button', { name: 'Schedule' })).toHaveCount(0)
    await shot(page, '08-ai-assistant-aisha')

    // publish: confirm dialog shows the exact text and the accounts; a double click posts once
    const posts: string[] = []
    page.on('request', (r) => { if (r.url().includes('/publish') && r.method() === 'POST') posts.push(r.url()) })
    await publish.getByRole('button', { name: /Publish to \d+ channels?/ }).click()
    const dialog = page.getByRole('dialog', { name: 'Publish this post?' })
    await expect(dialog.getByLabel('Post text')).toContainText('Light up the long evenings')
    await expect(dialog.getByLabel('Post text')).toContainText('The Lantern Pro lasts a full weekend on one charge.')
    await expect(dialog).toContainText('Company LinkedIn')
    await dialog.getByRole('button', { name: /^Publish to/ }).dblclick()
    const results = publish.getByRole('list', { name: 'Publishing results' })
    await expect(results.getByText('Simulated (development only)').first()).toBeVisible()
    await expect(results).toContainText(/nothing was posted/i)
    expect(posts.length).toBe(1)
    await shot(page, '08-ai-published-simulated-aisha')
    await context.close()
  })

  test('Connected accounts shows the cards and the publishing history', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'aisha.rahman', '/accounts')
    await expect(page.getByRole('heading', { name: 'Connected accounts' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'My LinkedIn' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Company LinkedIn page' })).toBeVisible()
    const instagram = page.getByRole('heading', { name: 'Instagram' }).locator('xpath=ancestor::section[1]')
    await expect(instagram).toContainText('Coming later')
    await expect(page.getByRole('heading', { name: 'Publishing history' })).toBeVisible()
    await expect(page.getByRole('table').getByText(/Simulated/).first()).toBeVisible()
    await expect(page.getByText('Light up the long evenings').first()).toBeVisible()
    await shot(page, '11-social-accounts-aisha')
    await context.close()
  })
})

test.describe('AI assistant as Lena Fischer (HR)', () => {
  test('has no Knowledge base, no Connected accounts and no publish picker, and gets no product sources', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'lena.fischer', '/ai')
    const nav = page.getByRole('navigation', { name: 'Main' })
    await expect(nav.getByRole('link', { name: 'AI assistant' })).toBeVisible()
    await expect(nav.getByRole('link', { name: 'Knowledge base' })).toHaveCount(0)
    await expect(nav.getByRole('link', { name: 'Connected accounts' })).toHaveCount(0)
    await page.goto('/knowledge')
    await expect(page.getByRole('heading', { name: 'You do not have access' })).toBeVisible()
    await page.goto('/accounts')
    await expect(page.getByRole('heading', { name: 'You do not have access' })).toBeVisible()

    await page.goto('/ai')
    await page.getByRole('textbox', { name: 'Message' }).fill('How do I pair the Lantern Pro with my phone?')
    await page.getByRole('button', { name: 'Send message' }).click()
    const log = page.getByRole('log', { name: 'Conversation' })
    await expect(log.getByText(/I do not know/)).toBeVisible()   // no collection she may use holds the answer
    await expect(log.locator('.source')).toHaveCount(0)

    await page.getByRole('textbox', { name: 'Message' }).fill('Write a LinkedIn post about leave')
    await page.getByRole('button', { name: 'Send message' }).click()
    await expect(page.getByRole('article', { name: 'Draft post' })).toBeVisible()
    await expect(page.getByRole('region', { name: 'Publish this draft' })).toHaveCount(0)   // no picker
    await expect(page.getByRole('button', { name: /^Publish to/ })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Copy text' })).toBeVisible()
    await context.close()
  })
})
