import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react'
import { fetchSession, loginWithCode, logoutSession } from '../api'
import type { SessionResponse, WebUser } from '../types/auth'

/**
 * Sign-in state: the pairing-code screen and the signed-in profile.
 *
 * Players get a code from `/colonyweb sync` in-game; there is no password to manage here.
 */
interface AuthCtx {
  /** Null until /auth/me has answered — the page shows a loader meanwhile. */
  session: SessionResponse | null
  authReady: boolean
  signedIn: boolean
  profile: WebUser | null

  loginCode: string
  loginError: string
  loggingIn: boolean

  setLoginCode: (v: string) => void
  submitLogin: () => Promise<void>
  signOut: () => Promise<void>
  /** Called when the server rejects a request mid-session (expired cookie, /colonyweb logout). */
  onUnauthorized: () => void
}

const SIGNED_OUT: SessionResponse = { authEnabled: true, authenticated: false, user: null }

const AuthContext = createContext<AuthCtx | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionResponse | null>(null)
  const [loginCode, setLoginCodeRaw] = useState('')
  const [loginError, setLoginError] = useState('')
  const [loggingIn, setLoggingIn] = useState(false)

  // Ask the server who we are. A failure just shows the sign-in screen.
  useEffect(() => {
    let cancelled = false
    fetchSession()
      .then(s => { if (!cancelled) setSession(s) })
      .catch(() => { if (!cancelled) setSession(SIGNED_OUT) })
    return () => { cancelled = true }
  }, [])

  /** Codes are typed as XXXX-XXXX; accept any casing and re-insert the dash. */
  const setLoginCode = useCallback((value: string) => {
    const raw = value.replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, 8)
    setLoginCodeRaw(raw.length > 4 ? `${raw.slice(0, 4)}-${raw.slice(4)}` : raw)
  }, [])

  const submitLogin = useCallback(async () => {
    if (loggingIn) return
    setLoginError('')
    setLoggingIn(true)
    try {
      const res = await loginWithCode(loginCode.trim())
      if (!res.ok) {
        setLoginError(res.data.error || 'That code was not accepted.')
        return
      }
      // /auth/login answers with the session document itself.
      setSession({ authEnabled: true, authenticated: true, user: res.data.user ?? null })
      setLoginCodeRaw('')
    } catch {
      setLoginError('Could not reach the server. Is it still running?')
    } finally {
      setLoggingIn(false)
    }
  }, [loginCode, loggingIn])

  const signOut = useCallback(async () => {
    try {
      await logoutSession()
    } catch {
      // Even if the call fails, drop local state so the viewer is not stuck.
    }
    setSession(SIGNED_OUT)
  }, [])

  const onUnauthorized = useCallback(() => setSession(SIGNED_OUT), [])

  const signedIn = !!session?.authenticated
  const profile = signedIn ? session?.user ?? null : null

  return (
    <AuthContext.Provider value={{
      session,
      authReady: session !== null,
      signedIn,
      profile,
      loginCode,
      loginError,
      loggingIn,
      setLoginCode,
      submitLogin,
      signOut,
      onUnauthorized,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
