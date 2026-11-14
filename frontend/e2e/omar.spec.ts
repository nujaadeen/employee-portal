import { expect, test } from '@playwright/test'
import { asUser, shot, uniqueWorkday } from './helpers'

test.describe('Omar Haddad (MANAGER)', () => {
  test('My team lists exactly his five direct reports', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'omar.haddad', '/employees')
    const myTeam = page.getByRole('tab', { name: /My team/ })
    await expect(myTeam).toHaveAttribute('aria-selected', 'true')
    const table = page.getByRole('table', { name: 'My team' })
    await expect(table.getByRole('button', { name: /^Show / })).toHaveCount(5)
    for (const name of ['Aisha Rahman', 'Karim Aziz', 'Nadia Hussein', 'Joel Mathews', 'Hana Saleh'])
      await expect(table.getByRole('button', { name: `Show ${name}` })).toBeVisible()
    for (const other of ['Ruth Mensah', 'Lena Fischer', 'Daniel Okoye', 'Priya Nair']) await expect(table.getByText(other)).toHaveCount(0)
    await expect(page.getByText(/Showing the people who report to you/)).toBeVisible()
    await expect(page.getByRole('tab', { name: /All employees/ })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Invite employee' })).toHaveCount(0)
    await page.getByRole('button', { name: 'Show Karim Aziz' }).click()
    await expect(page.getByRole('complementary', { name: 'Profile of Karim Aziz' }).getByText('EMP-0157')).toBeVisible()
    await shot(page, '05-employees-my-team-omar')
    await context.close()
  })

  test('a leave request goes from Aisha to Omar and back', async ({ browser }) => {
    const day = uniqueWorkday()
    const aisha = await asUser(browser, 'aisha.rahman', '/leave')
    await aisha.page.getByLabel('From', { exact: true }).fill(day)
    await aisha.page.getByLabel('To', { exact: true }).fill(day)
    await expect(aisha.page.getByLabel('Request summary')).toContainText('1')   // live working-day count
    await aisha.page.getByLabel('Reason (optional)').fill('Visa appointment')
    await aisha.page.getByRole('button', { name: 'Submit request' }).click()
    await expect(aisha.page.getByText('Request submitted')).toBeVisible()
    await expect(aisha.page.getByRole('heading', { name: 'My requests' })).toBeVisible()
    await expect(aisha.page.getByText('Pending').first()).toBeVisible()

    const omar = await asUser(browser, 'omar.haddad', '/leave')
    const row = omar.page.getByRole('row', { name: /Aisha Rahman/ }).first()
    await expect(row).toBeVisible()
    await shot(omar.page, '03-leave-omar')
    await omar.page.getByRole('button', { name: 'Approve Aisha Rahman' }).first().click()
    await omar.page.getByLabel('Note (optional)').fill('Enjoy')
    await omar.page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click()
    await expect(omar.page.getByRole('row', { name: /Aisha Rahman.*Approved/ }).first()).toBeVisible()

    await aisha.page.reload()
    await expect(aisha.page.getByRole('navigation', { name: 'Main' })).toBeVisible()
    await expect(aisha.page.getByText('Approved').first()).toBeVisible()
    await aisha.page.getByRole('button', { name: /Notifications/ }).click()
    await expect(aisha.page.getByRole('dialog', { name: 'Notifications' })).toContainText('approved')
    await aisha.context.close()
    await omar.context.close()
  })
})
