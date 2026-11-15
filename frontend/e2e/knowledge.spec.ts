import { expect, test } from '@playwright/test'
import { asUser, makePdf, shot } from './helpers'

test.describe('Knowledge base as Daniel Okoye (ADMIN)', () => {
  test('uploads a PDF, sees Indexing then Ready, and the document count changes', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'daniel.okoye', '/knowledge')
    await expect(page.getByRole('heading', { name: 'Knowledge base' })).toBeVisible()
    const table = page.getByRole('table')
    const row = table.getByRole('row').filter({ has: page.getByRole('button', { name: 'Product catalogue' }) })
    await expect(row).toBeVisible()
    const before = Number(await row.getByRole('cell').first().innerText())
    await shot(page, '10-knowledge-base-daniel')

    // a bad file is refused before anything is sent
    await page.getByRole('button', { name: 'Add source' }).first().click()
    const dialog = page.getByRole('dialog', { name: 'Add source' })
    await dialog.getByLabel('Add to collection').selectOption({ label: 'Product catalogue' })
    await dialog.getByLabel('PDF files').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') })
    await expect(dialog.getByRole('alert')).toContainText('notes.txt is not a PDF')
    await expect(dialog.getByRole('button', { name: /^Upload/ })).toBeDisabled()

    // a real PDF, many pages so the indexing is visible for a moment
    const name = `e2e-${Date.now()}.pdf`
    await dialog.getByLabel('PDF files').setInputFiles({ name, mimeType: 'application/pdf', buffer: makePdf(['Payout day for merchants can be changed once per month.', 'Refunds take three to five working days to arrive.'], 60) })
    await dialog.getByRole('button', { name: /^Upload/ }).click()
    await expect(dialog.getByText('Sent. Indexing in the background.')).toBeVisible()
    await dialog.getByRole('button', { name: 'Close' }).click()

    const activity = page.getByRole('region', { name: 'Indexing activity' }).or(page.locator('section', { has: page.getByRole('heading', { name: 'Indexing activity' }) }))
    const item = activity.getByRole('listitem').filter({ hasText: name })
    await expect(item).toBeVisible()
    await expect(item.getByText(/Waiting to index|Indexing/)).toBeVisible()   // while the job runs
    await expect(item.getByText('Ready')).toBeVisible({ timeout: 60_000 })   // polled every 3 s
    await shot(page, '10-knowledge-base-indexed-daniel')

    await expect(async () => { expect(Number(await row.getByRole('cell').first().innerText())).toBe(before + 1) }).toPass({ timeout: 20_000 })
    // the document shows in the detail panel and can be deleted (with a confirm)
    await page.getByRole('button', { name: 'Product catalogue' }).click()
    await page.getByText(/All documents \(/).click()
    await page.getByRole('button', { name: `Delete ${name}` }).click()
    await expect(page.getByRole('dialog', { name: 'Delete this document?' })).toContainText('cannot be undone')
    await page.getByRole('button', { name: 'Delete document' }).click()
    await expect(page.getByText('Document deleted.')).toBeVisible()
    await expect(async () => { expect(Number(await row.getByRole('cell').first().innerText())).toBe(before) }).toPass({ timeout: 20_000 })
    await context.close()
  })

  test('role chips are toggled and saved, a new collection is validated like the server, and Drive and website tabs validate', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'daniel.okoye', '/knowledge')
    await page.getByRole('button', { name: 'HR policies' }).click()
    const roles = page.getByRole('group', { name: 'Roles with access' })
    const manager = roles.getByRole('button', { name: 'Manager' })
    await expect(manager).toHaveAttribute('aria-pressed', 'false')
    await expect(roles.getByRole('button', { name: 'Admin' })).toBeDisabled()   // ADMIN always keeps access
    await expect(page.getByText(/decides who can search the collection/)).toBeVisible()
    try {
      await manager.click()
      await page.getByRole('button', { name: 'Save roles' }).click()
      await expect(page.getByText(/Saved\. 3 roles can now search HR policies\./)).toBeVisible()
    } finally {
      // put it back whatever happened
      if ((await manager.getAttribute('aria-pressed')) === 'true') {
        await manager.click()
        await page.getByRole('button', { name: 'Save roles' }).click()
        await expect(page.getByText(/Saved\. 2 roles can now search HR policies\./)).toBeVisible()
      }
    }

    await page.getByRole('button', { name: 'New collection' }).click()
    const nc = page.getByRole('dialog', { name: 'New collection' })
    await nc.getByLabel('Name').fill('Bad one')
    await nc.getByLabel('Code').fill('1x')
    await nc.getByRole('button', { name: 'Create collection' }).click()
    await expect(nc.getByRole('alert')).toContainText('3 to 40 characters')
    await nc.getByLabel('Code').fill('product')   // exists: the server says so next to the field
    await nc.getByRole('button', { name: 'Create collection' }).click()
    await expect(nc.getByRole('alert')).toContainText('already exists')
    await nc.getByRole('button', { name: 'Cancel' }).click()

    await page.getByRole('button', { name: 'Add source' }).first().click()
    const dialog = page.getByRole('dialog', { name: 'Add source' })
    await dialog.getByRole('tab', { name: 'Google Drive folder' }).click()
    await dialog.getByRole('button', { name: 'Start sync' }).click()
    await expect(dialog.getByRole('alert')).toContainText('Enter the folder ID')
    await dialog.getByText('Where do I find the folder ID?').click()
    await expect(dialog.getByText(/after \/folders\//)).toBeVisible()
    await expect(dialog.getByRole('link', { name: /Open Google Drive/ })).toHaveAttribute('href', /drive\.google\.com/)
    await dialog.getByRole('tab', { name: 'Website' }).click()
    await dialog.getByLabel('Web addresses').fill('https://a.example/\nhttps://b.example/')
    await dialog.getByRole('button', { name: 'Index website' }).click()
    await expect(dialog.getByRole('alert')).toContainText('same website')
    await context.close()
  })

  test('the Knowledge base fits a phone', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    const { signIn } = await import('./helpers')
    await signIn(page, 'daniel.okoye', '/knowledge')
    await expect(page.getByRole('heading', { name: 'Knowledge base' })).toBeVisible()
    await page.waitForLoadState('networkidle')
    for (const path of ['/knowledge', '/accounts', '/ai']) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(400)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      expect(overflow, `${path} scrolls sideways by ${overflow}px`).toBeLessThanOrEqual(0)
    }
    await context.close()
  })
})
