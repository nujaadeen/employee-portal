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

/** A small valid PDF with the given lines of text on each of `pages` pages (hand written, no library). */
export function makePdf(lines: string[], pages = 1): Buffer {
  const objs: string[] = []
  const pageIds: number[] = []
  objs.push('')   // 1 catalog, set below
  objs.push('')   // 2 pages, set below
  objs.push('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')   // 3
  for (let p = 0; p < pages; p++) {
    const text = lines.map((l, i) => `BT /F1 12 Tf 50 ${780 - i * 16} Td (${l.replace(/[()\\]/g, '')} p${p + 1}) Tj ET`).join('\n')
    const content = objs.length + 2
    pageIds.push(objs.length + 1)
    objs.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents ${content} 0 R >>`)
    objs.push(`<< /Length ${text.length} >>\nstream\n${text}\nendstream`)
  }
  objs[0] = '<< /Type /Catalog /Pages 2 0 R >>'
  objs[1] = `<< /Type /Pages /Kids [${pageIds.map((i) => `${i} 0 R`).join(' ')}] /Count ${pages} >>`
  let out = '%PDF-1.4\n'
  const offsets: number[] = []
  objs.forEach((o, i) => { offsets.push(out.length); out += `${i + 1} 0 obj\n${o}\nendobj\n` })
  const xref = out.length
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n${offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`
  out += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return Buffer.from(out, 'latin1')
}
