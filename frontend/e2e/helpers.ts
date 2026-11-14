import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'

export const PASSWORD = 'ChangeMe123!'

/** Signs in through the Baracel Keycloak login page. */
export async function signIn(page: Page, username: string, path = '/') {
  await page.goto(path)
  await page.waitForURL(/\/realms\/baracel\//)
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#kc-login').click()
  await page.waitForURL(/localhost:5173/)
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible()
}

export async function signOut(page: Page) {
  await page.context().clearCookies()
}
