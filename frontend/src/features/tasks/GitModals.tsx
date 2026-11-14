import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { usePermission } from '@/auth/me'
import { Button } from '@/ui/Button'
import { Field } from '@/ui/Field'
import { Modal } from '@/ui/Modal'
import { Select } from '@/ui/Select'

/** Repo picker: a list when the person may view repositories, otherwise a text field (backend gap, see docs/backend-gaps.md). */
function RepoField({ value, onChange, error }: { value: string; onChange: (v: string) => void; error?: string | null }) {
  const canList = usePermission('repo:view')
  const repos = useQuery({ queryKey: ['repos'], enabled: canList, queryFn: () => unwrap(api.GET('/api/repos')) })
  if (canList && (repos.data?.length ?? 0) > 0)
    return <Select label="Repository" value={value} onChange={(e) => onChange(e.target.value)} options={repos.data!.map((r) => ({ value: r.fullName!, label: r.fullName! }))} placeholder="Choose a repository" error={error} />
  return <Field label="Repository" value={value} onChange={(e) => onChange(e.target.value)} placeholder="owner/name" hint="For example baracel/otp-service" error={error} />
}

type Props = { open: boolean; onClose: () => void; taskKey: string; defaultRepo: string }

export function CreateBranchModal({ open, onClose, taskKey, defaultRepo }: Props) {
  const qc = useQueryClient()
  const [repo, setRepo] = useState(defaultRepo)
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/tasks/{key}/git/branch', { params: { path: { key: taskKey } }, body: { repo: repo || undefined } })),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['task', taskKey] }); void qc.invalidateQueries({ queryKey: ['board'] }); onClose() },
  })
  return (
    <Modal open={open} onClose={onClose} title="Create branch" description={`Gitea creates a branch named after ${taskKey} from the default branch.`}
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} onClick={() => m.mutate()}>Create branch</Button></>}>
      <RepoField value={repo} onChange={setRepo} error={m.error instanceof ApiError ? m.error.message : null} />
    </Modal>
  )
}

export function LinkPrModal({ open, onClose, taskKey, defaultRepo }: Props) {
  const qc = useQueryClient()
  const [repo, setRepo] = useState(defaultRepo)
  const [pr, setPr] = useState('')
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/tasks/{key}/git/link-pr', { params: { path: { key: taskKey } }, body: { repo, prNumber: Number(pr) } })),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['task', taskKey] }); void qc.invalidateQueries({ queryKey: ['board'] }); onClose() },
  })
  const err = m.error instanceof ApiError ? m.error.message : null
  return (
    <Modal open={open} onClose={onClose} title="Link a pull request" description="Gitea is asked for the pull request and its checks."
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} disabled={!repo || !pr} onClick={() => m.mutate()}>Link pull request</Button></>}>
      <div className="stack">
        <RepoField value={repo} onChange={setRepo} />
        <Field label="Pull request number" type="number" min={1} value={pr} onChange={(e) => setPr(e.target.value)} error={err} />
      </div>
    </Modal>
  )
}
