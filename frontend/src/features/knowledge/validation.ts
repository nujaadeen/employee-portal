/** The server refuses larger files (AI_MAX_UPLOAD_MB, 25 by default). Checked here so the person finds out before sending. */
export const MAX_UPLOAD_MB = 25

export function validatePdf(file: { name: string; type: string; size: number }, maxMb = MAX_UPLOAD_MB): string | null {
  const isPdf = file.type === 'application/pdf' || (file.type === '' && /\.pdf$/i.test(file.name))
  if (!isPdf) return `${file.name} is not a PDF. Only PDF files can be uploaded.`
  if (file.size === 0) return `${file.name} is empty.`
  if (file.size > maxMb * 1024 * 1024) return `${file.name} is ${(file.size / 1024 / 1024).toFixed(1)} MB. The limit is ${maxMb} MB.`
  return null
}

/** Same rule as the server: 3 to 40 characters, lowercase letters, digits and hyphens, starting with a letter. */
export const CODE_RE = /^[a-z][a-z0-9-]{2,39}$/
export function validateCode(code: string): string | null {
  if (!code) return 'Enter a code.'
  if (code.length < 3 || code.length > 40) return 'The code has 3 to 40 characters.'
  if (!CODE_RE.test(code)) return 'Use lowercase letters, digits and hyphens, and start with a letter.'
  return null
}
export const validateName = (name: string) => (name.trim() === '' ? 'Enter a name.' : name.length > 100 ? 'The name can have at most 100 characters.' : null)

/** A Google Drive folder id, or the whole folder address pasted from the browser. */
export function driveFolderId(input: string): string {
  const t = input.trim()
  const m = t.match(/\/folders\/([A-Za-z0-9_-]+)/)
  return m ? m[1]! : t
}
export function validateDriveFolder(input: string): string | null {
  const id = driveFolderId(input)
  if (!id) return 'Enter the folder ID.'
  if (!/^[A-Za-z0-9_-]{5,200}$/.test(id)) return 'A folder ID has letters, digits, hyphens and underscores only.'
  return null
}

/** One URL per line. Same domain only, http or https, like the server. */
export function parseUrls(text: string): { urls: string[]; error: string | null } {
  const urls = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  if (urls.length === 0) return { urls, error: 'Enter at least one address.' }
  if (urls.length > 50) return { urls, error: 'At most 50 addresses.' }
  let host = ''
  for (const u of urls) {
    let parsed: URL
    try { parsed = new URL(u) } catch { return { urls, error: `${u} is not a valid address.` } }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return { urls, error: 'Only http and https addresses work.' }
    host = host || parsed.hostname.toLowerCase()
    if (parsed.hostname.toLowerCase() !== host) return { urls, error: 'All addresses must be on the same website.' }
  }
  return { urls: [...new Set(urls)], error: null }
}

/** Toggle a role in the list. ADMIN always keeps access, like on the server. */
export function toggleRole(roles: string[], role: string): string[] {
  if (role === 'ADMIN') return roles.includes('ADMIN') ? roles : [...roles, 'ADMIN']
  return roles.includes(role) ? roles.filter((r) => r !== role) : [...roles, role]
}
