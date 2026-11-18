// Logs in through the Baracel Keycloak theme at http://portal.baracel.local and takes the verification screenshots into docs/kind/:
//   employees list as the Manager, the Sales AI chat while an answer streams, the Roles screen as Admin.
// Uses the Playwright of the frontend (cd frontend && npm ci). The browser maps *.baracel.local to 127.0.0.1, so no hosts file is needed.
//   node scripts/kind-screenshots.mjs
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const { chromium } = createRequire(path.join(root, 'frontend', 'package.json'))('@playwright/test')
const out = path.join(root, 'docs', 'kind')
mkdirSync(out, { recursive: true })
const PORTAL = 'http://portal.baracel.local'
const PASSWORD = 'ChangeMe123!'   // documented dev password of the demo users

// A browser only gives pages the Web Crypto API (crypto.subtle, needed for the PKCE login) on https or localhost. http://portal.baracel.local is
// neither, so this test browser is told to treat that one origin as secure. A person's browser needs the same (docs/kind.md, Troubleshooting).
const browser = await chromium.launch({ channel: 'chromium', args: ['--host-resolver-rules=MAP *.baracel.local 127.0.0.1', '--unsafely-treat-insecure-origin-as-secure=http://portal.baracel.local'] })

async function signIn(username, shotName) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  await page.goto(PORTAL + '/')
  await page.waitForURL(/auth\.baracel\.local\/realms\/baracel\//)
  if (shotName) await page.screenshot({ path: path.join(out, shotName) })
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#kc-login').click()
  await page.waitForURL(/portal\.baracel\.local/)
  await page.getByRole('navigation', { name: 'Main' }).waitFor()
  return { ctx, page }
}
async function shot(page, name) {
  await page.waitForLoadState('networkidle')
  const size = page.viewportSize()
  const height = await page.evaluate(() => Math.max(document.documentElement.scrollHeight, window.innerHeight))
  await page.setViewportSize({ width: size.width, height })
  await page.screenshot({ path: path.join(out, name) })
  await page.setViewportSize(size)
  console.log('saved docs/kind/' + name)
}

// 1. Manager: login page (Baracel theme at the cluster issuer) and the employee list, My team
{
  const { ctx, page } = await signIn('omar.haddad', '01-login-baracel-theme.png')
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Employees' }).click()
  await page.getByRole('tab', { name: /My team/ }).waitFor()
  const rows = page.getByRole('table').getByRole('row')
  await rows.nth(5).waitFor()
  await shot(page, '02-employees-manager.png')
  await ctx.close()
}

// 2. Sales: the AI chat while the answer streams
{
  const { ctx, page } = await signIn('aisha.rahman')
  await page.goto(PORTAL + '/ai')
  await page.getByRole('textbox', { name: 'Message' }).fill('Explain how to pair the Lantern Pro with my phone, how long the battery lasts and what the warranty covers.')
  await page.getByRole('button', { name: 'Send message' }).click()
  // take the picture while the stream is running: the Stop button is there and some text has arrived
  const stop = page.getByRole('button', { name: 'Stop generating' })
  await stop.waitFor({ timeout: 120000 })
  await page.waitForFunction(() => (Array.from(document.querySelectorAll('.ai__thread .answer')).pop()?.textContent ?? '').length > 25, null, { timeout: 120000 })
  const streaming = await stop.isVisible()
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.screenshot({ path: path.join(out, '03-ai-chat-streaming.png') })
  console.log('saved docs/kind/03-ai-chat-streaming.png (stream still running: ' + streaming + ')')
  await page.getByRole('button', { name: 'Stop generating' }).waitFor({ state: 'detached', timeout: 180000 })
  await shot(page, '03b-ai-chat-answered.png')
  await ctx.close()
}

// 3. Admin: Roles and access
{
  const { ctx, page } = await signIn('daniel.okoye')
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Roles and access' }).click()
  await page.getByRole('heading', { name: 'Roles and access' }).waitFor()
  await page.getByRole('table').first().waitFor()
  await shot(page, '04-roles-admin.png')
  await ctx.close()
}
await browser.close()
