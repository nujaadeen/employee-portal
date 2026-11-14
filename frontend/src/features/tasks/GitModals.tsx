import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import { Button } from '@/ui/Button'
import { Field } from '@/ui/Field'
import { Modal } from '@/ui/Modal'
import { Select } from '@/ui/Select'
import { useToast } from '@/ui/Toast'

/** Repositories linked to the task's project (GET /api/projects/{key}/repos). Only these are accepted by the server. */
export function useProjectRepos(projectKey: string, enabled = true) {
  return useQuery({
    queryKey: ['project-repos', projectKey], enabled: enabled && !!projectKey,
    queryFn: () => unwrap(api.GET('/api/projects/{key}/repos', { params: { path: { key: projectKey } } })),
  })
}

function RepoSelect({ projectKey, open, value, onChange, error }: { projectKey: string; open: boolean; value: string; onChange: (v: string) => void; error?: string | null }) {
  const repos = useProjectRepos(projectKey, open)
  const list = repos.data ?? []
  if (repos.isError) return <p className="note note--danger" role="alert">Could not load the project's repositories. {repos.error.message}</p>
  if (repos.isSuccess && list.length === 0)
    return <p className="note note--warning" role="status">No repository is linked to {projectKey} yet. Ask an administrator to link one.</p>
  const chosen = value || (list.length === 1 ? list[0]?.fullName ?? '' : '')
  return (
    <Select label="Repository" value={chosen} onChange={(e) => onChange(e.target.value)} placeholder={repos.isPending ? 'Loading repositories…' : 'Choose a repository'}
      options={list.map((r) => ({ value: r.fullName, label: r.fullName }))} error={error} />
  )
}

type Props = { open: boolean; onClose: () => void; taskKey: string; projectKey: string; defaultRepo: string }

export function CreateBranchModal({ open, onClose, taskKey, projectKey, defaultRepo }: Props) {
  const qc = useQueryClient()
  const toast = useToast()
  const [repo, setRepo] = useState(defaultRepo)
  const repos = useProjectRepos(projectKey, open)
  const chosen = repo || (repos.data?.length === 1 ? repos.data[0]?.fullName ?? '' : '')
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/tasks/{key}/git/branch', { params: { path: { key: taskKey } }, body: { repo: chosen || undefined } })),
    onSuccess: (l) => { toast.success(`Branch ${l.branch} created`); void qc.invalidateQueries({ queryKey: ['task', taskKey] }); void qc.invalidateQueries({ queryKey: ['board'] }); onClose() },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not create the branch.'),
  })
  return (
    <Modal open={open} onClose={onClose} title="Create branch" description={`Gitea creates a branch named after ${taskKey} from the default branch.`}
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} disabled={!chosen} onClick={() => m.mutate()}>Create branch</Button></>}>
      <RepoSelect projectKey={projectKey} open={open} value={repo} onChange={setRepo} error={m.error instanceof ApiError ? m.error.message : null} />
    </Modal>
  )
}

export function LinkPrModal({ open, onClose, taskKey, projectKey, defaultRepo }: Props) {
  const qc = useQueryClient()
  const toast = useToast()
  const [repo, setRepo] = useState(defaultRepo)
  const [pr, setPr] = useState('')
  const repos = useProjectRepos(projectKey, open)
  const chosen = repo || (repos.data?.length === 1 ? repos.data[0]?.fullName ?? '' : '')
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/tasks/{key}/git/link-pr', { params: { path: { key: taskKey } }, body: { repo: chosen, prNumber: Number(pr) } })),
    onSuccess: (l) => { toast.success(`Pull request #${l.prNumber} linked`); void qc.invalidateQueries({ queryKey: ['task', taskKey] }); void qc.invalidateQueries({ queryKey: ['board'] }); onClose() },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : 'Could not link the pull request.'),
  })
  const err = m.error instanceof ApiError ? m.error.message : null
  return (
    <Modal open={open} onClose={onClose} title="Link a pull request" description="Gitea is asked for the pull request and its checks."
      actions={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={m.isPending} disabled={!chosen || !pr} onClick={() => m.mutate()}>Link pull request</Button></>}>
      <div className="stack">
        <RepoSelect projectKey={projectKey} open={open} value={repo} onChange={setRepo} />
        <Field label="Pull request number" type="number" min={1} value={pr} onChange={(e) => setPr(e.target.value)} error={err} />
      </div>
    </Modal>
  )
}
