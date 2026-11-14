import type { Board } from '@/api/types'
import { moveCard } from './Board'
import { prChip } from './GitChips'

const board: Board = {
  taskCount: 2, linkedToGitea: 0,
  columns: [
    { status: 'BACKLOG', label: 'Backlog', count: 1, tasks: [{ task: { key: 'BAR-1', status: 'BACKLOG' } }] },
    { status: 'IN_PROGRESS', label: 'In progress', count: 1, tasks: [{ task: { key: 'BAR-2', status: 'IN_PROGRESS' } }] },
    { status: 'DONE', label: 'Done', count: 0, tasks: [] },
  ],
}

describe('board', () => {
  it('moves a card between columns and keeps the counts right', () => {
    const next = moveCard(board, 'BAR-1', 'DONE')
    expect(next.columns!.map((c) => c.count)).toEqual([0, 1, 1])
    expect(next.columns![2]!.tasks![0]!.task!.status).toBe('DONE')
    expect(board.columns![0]!.count).toBe(1)   // the original is not mutated
  })
  it('ignores an unknown key', () => { expect(moveCard(board, 'NOPE-9', 'DONE')).toBe(board) })
})

describe('pull request chips', () => {
  it('matches the screen copy', () => {
    expect(prChip({ prNumber: 57, prState: 'OPEN', checksStatus: 'PASSING' })).toEqual({ text: 'PR #57 open · checks passing', tone: 'success' })
    expect(prChip({ prNumber: 54, prState: 'OPEN', approvals: 0 })).toEqual({ text: 'PR #54 · 1 approval needed', tone: 'warning' })
    expect(prChip({ prNumber: 55, prState: 'OPEN', checksStatus: 'FAILING' })).toEqual({ text: 'PR #55 · 1 check failing', tone: 'danger' })
    expect(prChip({ prNumber: 51, prState: 'MERGED' })).toEqual({ text: 'PR #51 merged', tone: 'success' })
    expect(prChip({ branch: 'feature/x' })).toBeNull()
  })
})
