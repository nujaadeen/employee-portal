import { expect, test } from '@playwright/test'
import { asUser, shot } from './helpers'

test.describe('Lena Fischer (HR) and Daniel Okoye (ADMIN)', () => {
  test('Lena sees All employees with the HR actions', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'lena.fischer', '/employees')
    await expect(page.getByRole('tab', { name: /All employees/ })).toHaveAttribute('aria-selected', 'true')
    const table = page.getByRole('table', { name: 'All employees' })
    await expect(table.getByRole('button', { name: /^Show / })).toHaveCount(13)
    await expect(page.getByRole('button', { name: 'Invite employee' })).toBeVisible()
    await expect(page.getByRole('button', { name: /Edit record/ })).toBeVisible()
    await expect(page.getByText('Sign-in methods')).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Roles and access' })).toHaveCount(0)
    await shot(page, '04-employees-all-lena')
    await context.close()
  })

  test('Daniel (admin) edits the role matrix and sees sign-in methods', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'daniel.okoye', '/access')
    await expect(page.getByRole('heading', { name: 'Roles and access' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Sales' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    const box = page.getByRole('checkbox', { name: 'Personal accounts: publish' })
    await expect(box).toHaveAttribute('aria-checked', 'true')
    await box.click()
    await expect(page.getByRole('button', { name: 'Save changes' })).toBeEnabled()
    await box.click()   // back to the saved state: nothing to save
    await expect(page.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    await expect(page.getByRole('switch', { name: 'Google sign-in' })).toBeVisible()
    await shot(page, '09-roles-access-daniel')
    await context.close()
  })
})
