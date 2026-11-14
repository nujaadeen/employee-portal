import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from '@/api/client'

/** Members of a project (GET /api/projects/{key}/members). Only people on this list can be assigned. */
export function useMembers(projectKey: string, enabled = true) {
  return useQuery({
    queryKey: ['members', projectKey],
    enabled: enabled && !!projectKey,
    queryFn: () => unwrap(api.GET('/api/projects/{key}/members', { params: { path: { key: projectKey } } })),
  })
}
