import { GitBranch, GitPullRequest } from 'lucide-react'
import type { components } from '@/api/schema'

type Git = components['schemas']['GitSummary']

export function prChip(g: Git): { text: string; tone: 'success' | 'warning' | 'danger' | 'neutral' } | null {
  if (!g.prNumber) return null
  const n = `PR #${g.prNumber}`
  if (g.prState === 'MERGED') return { text: `${n} merged`, tone: 'success' }
  if (g.prState === 'CLOSED') return { text: `${n} closed`, tone: 'neutral' }
  if (g.checksStatus === 'FAILING') return { text: `${n} · 1 check failing`, tone: 'danger' }
  if (g.checksStatus === 'PASSING') return { text: `${n} open · checks passing`, tone: 'success' }
  if (!g.approvals) return { text: `${n} · 1 approval needed`, tone: 'warning' }
  return { text: `${n} open`, tone: 'neutral' }
}

/** Branch and pull-request chips on a board card. A merged branch is hidden, like the screen. */
export function GitChips({ git }: { git?: Git | null }) {
  if (!git) return null
  const pr = prChip(git)
  return (
    <div className="git-chips">
      {git.branch && git.prState !== 'MERGED' ? <span className="git-chip git-chip--branch mono" title={git.branch}><GitBranch size={14} aria-hidden="true" /><span>{git.branch}</span></span> : null}
      {pr ? <span className={`git-chip git-chip--${pr.tone}`}><GitPullRequest size={14} aria-hidden="true" /><span>{pr.text}</span></span> : null}
    </div>
  )
}
