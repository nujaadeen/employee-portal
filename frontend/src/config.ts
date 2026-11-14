import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from '@/api/client'

/** Public settings from GET /api/config (app name, Gitea address for browsers, leave types). */
export function useConfig() {
  return useQuery({ queryKey: ['config'], queryFn: () => unwrap(api.GET('/api/config')), staleTime: 5 * 60_000 })
}

/** Joins the Gitea address from the config with a path, without double slashes. */
export function giteaLink(giteaUrl: string | undefined, path: string): string | undefined {
  return giteaUrl ? `${giteaUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}` : undefined
}
