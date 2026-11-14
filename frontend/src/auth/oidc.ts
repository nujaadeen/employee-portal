import { InMemoryWebStorage, UserManager, WebStorageStateStore } from 'oidc-client-ts'
import type { User } from 'oidc-client-ts'
import { env } from '@/env'

/**
 * Authorization Code flow with PKCE against Keycloak (client portal-web).
 * Tokens live in memory only. Only the short-lived login `state` (needed across the redirect) uses sessionStorage.
 * After a full page reload the user is sent through Keycloak again; the Keycloak session makes that instant.
 */
export const userManager = new UserManager({
  authority: env.oidcAuthority,
  client_id: env.oidcClientId,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true, // renews with the refresh token before the access token expires
})

let current: User | null = null
userManager.events.addUserLoaded((u) => { current = u })
userManager.events.addUserUnloaded(() => { current = null })
userManager.events.addSilentRenewError(() => { void startLogin() })

export function getAccessToken(): string | undefined {
  return current?.access_token
}

export function setCurrentUser(u: User | null) { current = u }

let redirecting = false
/** Sends the browser to the Keycloak login page. Remembers where to come back to. */
export function startLogin(returnTo = window.location.pathname + window.location.search) {
  if (redirecting) return Promise.resolve()
  redirecting = true
  return userManager.signinRedirect({ state: { returnTo } })
}

export function logout() {
  return userManager.signoutRedirect()
}
