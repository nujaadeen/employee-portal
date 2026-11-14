import type { RoleMatrix } from '@/api/types'

export const COLUMNS = ['VIEW', 'CREATE', 'EDIT', 'APPROVE', 'PUBLISH'] as const
export type Column = (typeof COLUMNS)[number]
export type MatrixRow = { label: string; hint: string; cells: Partial<Record<Column, { key: string }>> }
export type MatrixGroup = { name: string; rows: MatrixRow[] }

/** Groups the flat permission list into the screen's sections and rows. */
export function buildMatrix(perms: NonNullable<RoleMatrix['permissions']>): MatrixGroup[] {
  const groups: MatrixGroup[] = []
  for (const p of perms) {
    let g = groups.find((x) => x.name === p.group)
    if (!g) { g = { name: p.group, rows: [] }; groups.push(g) }
    let r = g.rows.find((x) => x.label === p.row)
    if (!r) { r = { label: p.row, hint: p.hint ?? '', cells: {} }; g.rows.push(r) }
    r.cells[p.column as Column] = { key: p.key }
  }
  return groups
}
