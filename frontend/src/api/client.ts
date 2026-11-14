import createClient from 'openapi-fetch'
import type { Middleware } from 'openapi-fetch'
import { getAccessToken, startLogin } from '@/auth/oidc'
import { env } from '@/env'
import type { paths } from './schema'

export type Problem = { type?: string; title?: string; detail?: string; status?: number }

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem
  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`)
    this.status = status
    this.problem = problem
  }
  /** Last path segment of the problem type, e.g. "pending-approval". */
  get slug() { return this.problem.type?.split('/').pop() ?? '' }
}

// ---- tiny store: the backend told us this account waits for HR approval ----
let pending = false
const listeners = new Set<() => void>()
export const pendingApproval = {
  get: () => pending,
  set(v: boolean) { if (pending !== v) { pending = v; listeners.forEach((l) => l()) } },
  subscribe(l: () => void) { listeners.add(l); return () => { listeners.delete(l) } },
}

/** The only place the bearer token is added and the only place 401 and pending-approval are handled. */
const auth: Middleware = {
  onRequest({ request }) {
    const token = getAccessToken()
    if (token) request.headers.set('Authorization', `Bearer ${token}`)
    return request
  },
  async onResponse({ response }) {
    if (response.status === 401) void startLogin()
    if (response.status === 403) {
      const body = (await response.clone().json().catch(() => ({}))) as Problem
      if (body.type?.endsWith('/pending-approval')) pendingApproval.set(true)
    }
    return response
  },
}

export const api = createClient<paths>({ baseUrl: env.apiUrl })
api.use(auth)

/** Turns an openapi-fetch result into data, or throws an ApiError carrying the RFC 7807 problem. */
export async function unwrap<T>(p: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await p
  if (error !== undefined || !response.ok) {
    throw new ApiError(response.status, (error ?? {}) as Problem)
  }
  return data as T
}
