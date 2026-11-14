import { test } from '@playwright/test'
import { signIn } from './helpers'

test('explore', async ({ page }) => {
  await page.goto('/')
  await page.waitForURL(/\/realms\/baracel\//)
  await page.waitForTimeout(800)
  await page.screenshot({ path: '/tmp/shots/login.png' })
  await signIn(page, process.env.U ?? 'aisha.rahman')
  for (const [name, path] of [['dashboard', '/'], ['leave', '/leave'], ['employees', '/employees'], ['tasks', '/tasks'], ['task', '/tasks/BAR-142'], ['ai', '/ai/d0000000-0000-4000-8000-000000000001'], ['profile', '/profile']] as const) {
    await page.goto(path)
    await page.waitForTimeout(1500)
    await page.screenshot({ path: `/tmp/shots/${process.env.U ?? 'aisha'}-${name}.png`, fullPage: true })
  }
})
