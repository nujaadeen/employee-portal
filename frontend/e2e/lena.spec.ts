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

  test('Lena resets access after a confirmation that says an email will be sent', async ({ browser }) => {
    const { context, page } = await asUser(browser, 'lena.fischer', '/employees')
    await page.getByRole('button', { name: 'Show Karim Aziz' }).click()
    await page.getByRole('button', { name: 'Reset access' }).click()
    const dialog = page.getByRole('dialog', { name: 'Reset access for Karim Aziz?' })
    await expect(dialog).toContainText('An email will be sent to karim.aziz@baracel.example')
    await dialog.getByRole('button', { name: 'Send email' }).click()
    await expect(page.getByText('Reset email sent')).toBeVisible()
    await expect(page.getByText('Managed by provider').first()).toBeVisible()
    await context.close()
  })

  test('Daniel creates and deletes a custom role', async ({ browser }) => {
    const name = 'TEST_' + Date.now().toString().slice(-6).replace(/\d/g, (d) => 'ABCDEFGHIJ'[Number(d)]!)
    const { context, page } = await asUser(browser, 'daniel.okoye', '/access')
    await page.getByRole('button', { name: 'New role' }).click()
    const dialog = page.getByRole('dialog', { name: 'New role' })
    await dialog.getByLabel('Name').fill('ab')
    await dialog.getByRole('button', { name: 'Create role' }).click()
    await expect(dialog.getByRole('alert')).toContainText('3 to 30 characters')   // the server's message, next to the field
    await dialog.getByLabel('Name').fill('sales')
    await dialog.getByRole('button', { name: 'Create role' }).click()
    await expect(dialog.getByRole('alert')).toContainText('already exists')
    await dialog.getByLabel('Name').fill(name)
    await dialog.getByLabel('Description (optional)').fill('Created by the end-to-end test')
    await dialog.getByRole('button', { name: 'Create role' }).click()
    await expect(page.getByText(`Role ${name} created`)).toBeVisible()
    await expect(page.getByRole('heading', { name })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    // built-in roles cannot be deleted: no button
    await page.getByRole('button', { name: /^Sales/ }).click()
    await expect(page.getByRole('button', { name: 'Delete role' })).toHaveCount(0)
    await page.getByRole('button', { name: new RegExp(name) }).click()
    await page.getByRole('button', { name: 'Delete role' }).click()
    await page.getByRole('dialog', { name: `Delete the ${name} role?` }).getByRole('button', { name: 'Delete role' }).click()
    await expect(page.getByText(`Role ${name} deleted`)).toBeVisible()
    await expect(page.getByRole('button', { name: new RegExp(name) })).toHaveCount(0)
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
