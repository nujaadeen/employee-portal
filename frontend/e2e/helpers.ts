import type { Browser, Page } from '@playwright/test'
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

/** A fresh browser context (own cookies) signed in as the given person. */
export async function asUser(browser: Browser, username: string, path = '/') {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  await signIn(page, username, path)
  return { context, page }
}

export async function shot(page: Page, name: string) {
  await page.waitForLoadState('networkidle')
  // Grow the viewport to the page height so the sticky sidebar spans the whole screenshot, like the design PNGs.
  const size = page.viewportSize() ?? { width: 1440, height: 900 }
  const height = await page.evaluate(() => Math.max(document.documentElement.scrollHeight, window.innerHeight))
  await page.setViewportSize({ width: size.width, height })
  await page.screenshot({ path: `e2e/screenshots/${name}.png` })
  await page.setViewportSize(size)
}

/** A working day far in the future that changes on every run, so repeated runs never overlap. */
export function uniqueWorkday(): string {
  const base = new Date(2027, 0, 4)
  base.setDate(base.getDate() + (Math.floor(Date.now() / 1000) % 900))
  while (base.getDay() === 0 || base.getDay() === 6) base.setDate(base.getDate() + 1)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${base.getFullYear()}-${p(base.getMonth() + 1)}-${p(base.getDate())}`
}
