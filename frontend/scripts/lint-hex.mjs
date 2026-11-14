// Fails when a source file under src/ contains a raw hex colour (#fff, #3261eb, ...).
// design/tokens.css is the only place colours are defined, and it is not inside src/.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const root = new URL('../src', import.meta.url).pathname
const hex = /(^|[^&\w/-])#[0-9a-fA-F]{3,8}\b/
const skip = (p) => p.endsWith('schema.d.ts') || p.includes('.test.')
const problems = []

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p)
    else if (/\.(tsx?|css)$/.test(name) && !skip(p)) {
      readFileSync(p, 'utf8').split('\n').forEach((line, i) => {
        if (hex.test(line)) problems.push(`${relative(process.cwd(), p)}:${i + 1}: ${line.trim()}`)
      })
    }
  }
}
walk(root)
if (problems.length) {
  console.error('Raw hex colours found. Use CSS variables from design/tokens.css:\n' + problems.join('\n'))
  process.exit(1)
}
console.log('lint-hex: no raw hex colours in src/')
