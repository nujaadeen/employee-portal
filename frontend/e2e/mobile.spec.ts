import { expect, test } from '@playwright/test'
import { shot } from './helpers'

test.use({ viewport: { width: 390, height: 844 } })

test('every screen fits a 390px phone without sideways scrolling and the menu is a drawer', async ({ page }) => {
  await page.goto('/')
  await page.waitForURL(/\/realms\/baracel\//)
  await shot(page, 'm-01-login-390')
  await page.locator('#username').fill('omar.haddad')
  await page.locator('#password').fill('ChangeMe123!')
  await page.locator('#kc-login').click()
  await page.waitForURL(/localhost:5173/)
  await expect(page.getByRole('button', { name: 'Open menu' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Main' })).not.toBeInViewport()
  await page.getByRole('button', { name: 'Open menu' }).click()
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeInViewport()
  await page.keyboard.press('Escape')

  for (const [name, path] of [['dashboard', '/'], ['leave', '/leave'], ['employees', '/employees'], ['tasks', '/tasks'], ['task', '/tasks/BAR-142'], ['ai', '/ai'], ['profile', '/profile']] as const) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(500)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, `${name} scrolls sideways by ${overflow}px`).toBeLessThanOrEqual(0)
    await shot(page, `m-${name}-390`)
  }
})

