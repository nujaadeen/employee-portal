import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Clock } from 'lucide-react'
import { useState } from 'react'
import type { DragEvent, KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, unwrap } from '@/api/client'
import type { Board as BoardData, TaskCard, TaskStatus } from '@/api/types'
import { shortDate, toIso, weekdayDate } from '@/lib/format'
import { Avatar } from '@/ui/Avatar'
import { StatusPill } from '@/ui/StatusPill'
import { GitChips } from './GitChips'
import { LABEL_TONE, STATUSES } from './statuses'

/** Moves a card in a cached board. Pure, so it is also used for the optimistic update. */
export function moveCard(board: BoardData, key: string, to: TaskStatus): BoardData {
  let card: TaskCard | undefined
  const columns = (board.columns ?? []).map((c) => {
    const found = c.tasks?.find((t) => t.task?.key === key)
    if (found) card = found
    return { ...c, tasks: (c.tasks ?? []).filter((t) => t.task?.key !== key) }
  })
  if (!card) return board
  const moved = { ...card, task: { ...card.task, status: to } }
  return { ...board, columns: columns.map((c) => { const tasks = c.status === to ? [...c.tasks, moved] : c.tasks; return { ...c, tasks, count: tasks.length } }) }
}

export function TaskCardView({ card, today, onMove, grabbed }: { card: TaskCard; today: string; onMove: (key: string, to: TaskStatus) => void; grabbed?: boolean }) {
  const t = card.task
  const idx = STATUSES.findIndex((s) => s.value === t.status)
  function onKey(e: KeyboardEvent) {
    if (!e.altKey) return
    const to = STATUSES[idx + (e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0)]
    if (to && (e.key === 'ArrowRight' || e.key === 'ArrowLeft')) { e.preventDefault(); onMove(t.key, to.value) }
  }
  const due = t.dueDate ? (t.dueDate === today ? 'Today' : t.status === 'DONE' ? shortDate(t.dueDate) : weekdayDate(t.dueDate)) : null
  return (
    <article className="task-card" draggable data-grabbed={grabbed} onKeyDown={onKey}
      onDragStart={(e: DragEvent) => { e.dataTransfer.setData('text/plain', t.key); e.dataTransfer.effectAllowed = 'move' }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="mono muted" style={{ fontSize: 'var(--text-xs)' }}>{t.key}</span>
        {t.label ? <StatusPill tone={LABEL_TONE[t.label] ?? 'neutral'}>{t.label}</StatusPill> : null}
      </div>
      <Link to={`/tasks/${t.key}`} className="task-card__title" aria-keyshortcuts="Alt+ArrowLeft Alt+ArrowRight">{t.title}</Link>
      <GitChips git={card.git} />
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="muted row" style={{ gap: 'var(--space-2)' }}>{due ? <><Clock size={16} aria-hidden="true" />{due}</> : null}</span>
        {t.assigneeName ? <span title={t.assigneeName}><Avatar name={t.assigneeName} size="sm" /><span className="sr-only">Assigned to {t.assigneeName}</span></span> : null}
      </div>
    </article>
  )
}

export function Board({ board, boardKey, mine, meId }: { board: BoardData; boardKey: readonly unknown[]; mine: boolean; meId?: string }) {
  const qc = useQueryClient()
  const today = toIso(new Date())
  const [announce, setAnnounce] = useState('')
  const [error, setError] = useState<string | null>(null)
  const m = useMutation({
    mutationFn: ({ key, to }: { key: string; to: TaskStatus }) => unwrap(api.PATCH('/api/tasks/{key}/status', { params: { path: { key } }, body: { status: to } })),
    onMutate: async ({ key, to }) => {
      await qc.cancelQueries({ queryKey: boardKey })
      const prev = qc.getQueryData<BoardData>(boardKey)
      if (prev) qc.setQueryData(boardKey, moveCard(prev, key, to))
      return { prev }
    },
    onError: (e, _v, ctx) => { if (ctx?.prev) qc.setQueryData(boardKey, ctx.prev); setError(`Could not move the task: ${e.message}`) },
    onSettled: () => { void qc.invalidateQueries({ queryKey: ['board'] }); void qc.invalidateQueries({ queryKey: ['dashboard'] }) },
  })
  const move = (key: string, to: TaskStatus) => {
    const label = STATUSES.find((s) => s.value === to)!.label
    setError(null); setAnnounce(`Moved ${key} to ${label}`); m.mutate({ key, to })
  }
  return (
    <>
      <p className="sr-only" role="status" aria-live="polite">{announce}</p>
      {error ? <div className="banner banner--danger" role="alert" style={{ marginBottom: 'var(--space-4)' }}>{error}</div> : null}
      <p className="muted board-hint">Drag a card to another column, or focus its title and press Alt + Left or Right arrow.</p>
      <div className="board" role="list">
        {(board.columns ?? []).map((col) => {
          const cards = (col.tasks ?? []).filter((c) => !mine || c.task?.assigneeId === meId)
          return (
            <section key={col.status} className="board__col" role="listitem" aria-label={`${col.label}, ${cards.length} tasks`}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => { e.preventDefault(); const key = e.dataTransfer.getData('text/plain'); if (key) move(key, col.status as TaskStatus) }}>
              <header className="board__head"><span className="label-caps">{col.label}</span><span className="label-caps">{cards.length}</span></header>
              <div className="board__cards">
                {cards.map((c) => <TaskCardView key={c.task.key} card={c} today={today} onMove={move} />)}
                {cards.length === 0 ? <p className="muted board__empty">No tasks</p> : null}
              </div>
            </section>
          )
        })}
      </div>
    </>
  )
}
