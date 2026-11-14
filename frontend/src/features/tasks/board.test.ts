import type { Board, TaskCard } from '@/api/types'
import type { components } from '@/api/schema'
import { moveCard } from './Board'
import { prChip } from './GitChips'

type Git = components['schemas']['GitSummary']
const git = (g: Partial<Git>): Git => ({ repo: 'baracel/otp-service', branch: 'feature/x', approvals: 0, ...g })
const card = (key: string, status: TaskCard['task']['status']): TaskCard => ({ task: { id: 1, key, title: key, priority: 'MEDIUM', status, subtasksDone: 0, subtasksTotal: 0 } })

const board: Board = {
  taskCount: 2, linkedToGitea: 0, project: { id: 1, key: 'BAR', name: 'Platform', members: 1 }, sprints: [],
  columns: [
    { status: 'BACKLOG', label: 'Backlog', count: 1, tasks: [card('BAR-1', 'BACKLOG')] },
    { status: 'IN_PROGRESS', label: 'In progress', count: 1, tasks: [card('BAR-2', 'IN_PROGRESS')] },
    { status: 'DONE', label: 'Done', count: 0, tasks: [] },
  ],
}

describe('board', () => {
  it('moves a card between columns and keeps the counts right', () => {
    const next = moveCard(board, 'BAR-1', 'DONE')
    expect(next.columns.map((c) => c.count)).toEqual([0, 1, 1])
    expect(next.columns![2]!.tasks![0]!.task.status).toBe('DONE')
    expect(board.columns![0]!.count).toBe(1)   // the original is not mutated
  })
  it('ignores an unknown key', () => { expect(moveCard(board, 'NOPE-9', 'DONE')).toBe(board) })
})

describe('pull request chips', () => {
  it('matches the screen copy', () => {
    expect(prChip(git({ prNumber: 57, prState: 'OPEN', checksStatus: 'PASSING' }))).toEqual({ text: 'PR #57 open · checks passing', tone: 'success' })
    expect(prChip(git({ prNumber: 54, prState: 'OPEN', approvals: 0 }))).toEqual({ text: 'PR #54 · 1 approval needed', tone: 'warning' })
    expect(prChip(git({ prNumber: 55, prState: 'OPEN', checksStatus: 'FAILING' }))).toEqual({ text: 'PR #55 · 1 check failing', tone: 'danger' })
    expect(prChip(git({ prNumber: 51, prState: 'MERGED' }))).toEqual({ text: 'PR #51 merged', tone: 'success' })
    expect(prChip(git({ branch: 'feature/x' }))).toBeNull()
  })
})
