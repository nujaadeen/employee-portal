import { expect, test } from '@playwright/test'
import { asUser, shot } from './helpers'

test.describe('Aisha Rahman (SALES)', () => {
  test('sees no My team, no approvals and no Roles and access', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'aisha.rahman')
    const nav = page.getByRole('navigation', { name: 'Main' })
    await expect(page.getByRole('heading', { name: /Good (morning|afternoon|evening), Aisha/ })).toBeVisible()
    await expect(nav.getByRole('link', { name: 'Roles and access' })).toHaveCount(0)
    await expect(nav.getByRole('link', { name: 'Task detail' })).toHaveCount(0)
    await shot(page, '02-dashboard-aisha')

    await nav.getByRole('link', { name: 'Employees' }).click()
    await expect(page.getByRole('tab', { name: /Directory/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /My team/ })).toHaveCount(0)
    await expect(page.getByRole('tab', { name: /All employees/ })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Invite employee' })).toHaveCount(0)
    await expect(page.getByRole('table', { name: 'Directory' })).toBeVisible()
    await shot(page, '04-employees-directory-aisha')

    await nav.getByRole('link', { name: 'Leave' }).click()
    await expect(page.getByRole('heading', { name: 'Request leave' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Requests to approve' })).toHaveCount(0)
    await expect(page.getByLabel('Annual balance')).toContainText(/\d+ of 30 days/)
    await shot(page, '03-leave-aisha')

    // typing the URL directly shows the 403 page; the backend would refuse as well
    await page.goto('/access')
    await expect(page.getByRole('heading', { name: 'You do not have access' })).toBeVisible()
    await shot(page, '10-forbidden-aisha')
    await context.close()
  })

  test('tasks board, task detail and moving a card', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'aisha.rahman', '/tasks')
    await expect(page.getByRole('heading', { name: /Sprint 23/ })).toBeVisible()
    await expect(page.getByText('BAR-142').first()).toBeVisible()
    await expect(page.getByText('localhost:3000')).toBeVisible()   // host of giteaUrl from /api/config
    await shot(page, '06-tasks-board-aisha')
    await page.getByRole('link', { name: 'WhatsApp OTP delivery' }).first().click()
    await expect(page.getByRole('heading', { name: 'BAR-142' })).toBeVisible()
    await expect(page.getByText('Webhook live')).toBeVisible()
    await expect(page.getByText('2 of 4 done')).toBeVisible()
    // Open in Gitea uses the public Gitea address from /api/config
    await expect(page.getByRole('link', { name: 'Open in Gitea' })).toHaveAttribute('href', /^http:\/\/localhost:3000\/baracel\/otp-service(\/pulls\/\d+)?$/)
    // the branch dialog offers the repositories linked to the project, not a free text box
    await page.getByRole('button', { name: 'Create branch' }).click()
    const repo = page.getByRole('dialog', { name: 'Create branch' }).getByLabel('Repository')
    await expect(repo.getByRole('option', { name: 'baracel/otp-service' })).toBeAttached()
    await expect(repo.getByRole('option', { name: 'baracel/portal' })).toBeAttached()
    await page.keyboard.press('Escape')
    await shot(page, '07-task-detail-aisha')

    // keyboard: Alt + arrow on the card title moves it one column, then move it back (works from any column)
    await page.goto('/tasks')
    const columns = ['Backlog', 'In progress', 'In review', 'Done']
    const title = page.getByRole('link', { name: 'Leave calendar export' })
    const columnOf = async () => {
      const label = await page.getByRole('listitem').filter({ has: page.getByRole('link', { name: 'Leave calendar export' }) }).first().getAttribute('aria-label')
      return columns.findIndex((c) => label?.startsWith(c))
    }
    const start = await columnOf()
    const forward = start < columns.length - 1
    await title.focus()
    await title.press(forward ? 'Alt+ArrowRight' : 'Alt+ArrowLeft')
    const moved = forward ? start + 1 : start - 1
    await expect(page.getByRole('listitem', { name: new RegExp(`^${columns[moved]}`) }).getByText('Leave calendar export')).toBeVisible()
    await page.getByRole('link', { name: 'Leave calendar export' }).press(forward ? 'Alt+ArrowLeft' : 'Alt+ArrowRight')
    await expect(page.getByRole('listitem', { name: new RegExp(`^${columns[start]}`) }).getByText('Leave calendar export')).toBeVisible()
    await context.close()
  })

  test('AI draft can be published to two channels and is marked as simulated', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'aisha.rahman', '/ai')
    await expect(page.getByRole('heading', { name: 'AI assistant' })).toBeVisible()
    await page.getByLabel('Message', { exact: true }).fill('Write an Instagram and LinkedIn ad for our new merchant QR payments product')
    await page.getByRole('button', { name: 'Send message' }).click()
    await expect(page.getByText('Scan. Pay. Done.')).toBeVisible()
    await expect(page.getByText('Product brief v3.pdf')).toBeVisible()
    const instagram = page.getByRole('button', { name: /Company Instagram/ })
    await expect(instagram).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('button', { name: /Company LinkedIn/ })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('button', { name: /Schedule/ })).toBeDisabled()
    await page.getByRole('button', { name: 'Publish to 2 channels' }).click()
    await expect(page.getByText('Simulated: nothing was posted.')).toBeVisible()
    // right panel: HR and Engineering knowledge are locked for Sales
    await expect(page.getByLabel('What you can access').getByText('No access')).toHaveCount(2)
    await shot(page, '08-ai-assistant-aisha')
    await context.close()
  })

  test('profile page and sign out through Keycloak', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'aisha.rahman', '/profile')
    await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible()
    await shot(page, '11-profile-aisha')
    await page.getByRole('button', { name: 'Sign out' }).click()
    await page.waitForURL(/\/realms\/baracel\//)
    await expect(page.locator('#kc-login')).toBeVisible()
    await context.close()
  })
})
