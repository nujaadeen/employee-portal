// Signs in through the PUBLIC address like a teammate would (basic auth, ngrok warning page, Keycloak, a streamed AI answer) and takes a screenshot.
// Credentials are read from infra/public.env and never printed.   node scripts/kind-public-login.mjs [username]
import { createRequire } from 'node:module'
import { readFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const { chromium } = createRequire(path.join(root, 'frontend', 'package.json'))('@playwright/test')
const env = Object.fromEntries(readFileSync(path.join(root, 'infra', 'public.env'), 'utf8').split('\n').filter((l) => /^[A-Z_]+=/.test(l)).map((l) => [l.split('=')[0], l.slice(l.indexOf('=') + 1)]))
const user = process.argv[2] ?? 'aisha.rahman'
const out = path.join(root, 'docs', 'ngrok')
mkdirSync(out, { recursive: true })
const base = `https://${env.NGROK_DOMAIN}`

const browser = await chromium.launch()   // no flags: https is a secure context
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, httpCredentials: { username: env.PUBLIC_BASIC_USER, password: env.PUBLIC_BASIC_PASSWORD } })
const page = await ctx.newPage()
await page.goto(base + '/')
// ngrok free plan warning page: "Visit Site" (a button or a link) once per browser
await page.getByText('Visit Site').first().click({ timeout: 20000 }).catch(() => console.log('(no ngrok warning page)'))
await page.waitForURL(/\/auth\/realms\/baracel\//, { timeout: 60000 })
console.log('redirected to the Keycloak login page on the public address')
await page.screenshot({ path: path.join(out, '01-login-public.png') })
await page.locator('#username').fill(user)
await page.locator('#password').fill(env.PUBLIC_DEMO_PASSWORD)
await page.locator('#kc-login').click()
await page.getByRole('navigation', { name: 'Main' }).waitFor({ timeout: 60000 })
console.log(`signed in as ${user}`)
await page.goto(base + '/ai')
await page.getByRole('textbox', { name: 'Message' }).fill('How do I pair the Lantern Pro with my phone?')
await page.getByRole('button', { name: 'Send message' }).click()
await page.locator('.ai__thread .answer').last().filter({ hasText: /\S{3,}/ }).waitFor({ timeout: 180000 })
await page.getByRole('button', { name: 'Stop generating' }).waitFor({ state: 'detached', timeout: 180000 })
console.log('streamed AI answer received through the public address')
await page.screenshot({ path: path.join(out, '02-ai-answer-public.png') })
await browser.close()
