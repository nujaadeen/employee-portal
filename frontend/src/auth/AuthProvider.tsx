import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { setCurrentUser, startLogin, userManager } from './oidc'

type State = { status: 'loading' } | { status: 'ready' } | { status: 'error'; message: string }

// React StrictMode runs effects twice in dev; the callback code may only be used once.
let callbackOnce: Promise<string> | null = null

async function boot(): Promise<'ready' | 'redirecting'> {
  if (window.location.pathname === '/callback') {
    callbackOnce ??= userManager.signinCallback().then((user) => {
      if (!user) throw new Error('Sign-in did not return a user.')
      setCurrentUser(user)
      const state = user.state as { returnTo?: string } | undefined
      return state?.returnTo ?? '/'
    })
    const returnTo = await callbackOnce
    window.history.replaceState({}, '', returnTo)
    return 'ready'
  }
  const user = await userManager.getUser()
  if (user && !user.expired) { setCurrentUser(user); return 'ready' }
  await startLogin()
  return 'redirecting'
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ status: 'loading' })
  useEffect(() => {
    boot().then(
      (r) => { if (r === 'ready') setState({ status: 'ready' }) },
      (e: unknown) => setState({ status: 'error', message: e instanceof Error ? e.message : 'Sign-in failed.' }),
    )
  }, [])
  if (state.status === 'ready') return <>{children}</>
  if (state.status === 'error') {
    return (
      <div className="status-page" role="alert">
        <h1>Sign-in problem</h1>
        <p className="muted">{state.message}</p>
        <button className="btn btn--primary" onClick={() => { window.location.assign('/') }}>Try again</button>
      </div>
    )
  }
  return <div className="status-page" role="status"><p className="muted">Signing you in…</p></div>
}
