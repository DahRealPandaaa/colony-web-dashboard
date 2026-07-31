import type { FormEvent } from 'react'
import { useAuth } from '../../contexts/AuthContext'
import House from '../../components/icons/House'

/** Sign-in: pairing codes come from /colonyweb sync in-game, so there is nothing to remember. */
export function LoginForm() {
  const { loginCode, setLoginCode, loginError, loggingIn, submitLogin } = useAuth()

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    submitLogin()
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="flex items-center gap-3 mb-6">
          <span className="brand-mark">
            <House />
          </span>
          <span>
            <span className="brand-name block">ColonyWeb</span>
            <span className="brand-sub block">MineColonies dashboard</span>
          </span>
        </div>

        <h1 className="text-xl font-bold tracking-tight font-display">Sign in with a pairing code</h1>
        <p className="text-sm text-text-secondary mt-1.5 leading-relaxed">
          Run <span className="kbd">/colonyweb sync</span> in-game and type the code it gives you.
          It is valid once, and only for the colonies you belong to.
        </p>

        <form className="mt-6" onSubmit={onSubmit}>
          <label className="tile-label block mb-2" htmlFor="login-code">Pairing code</label>
          <input
            id="login-code"
            className="code-input"
            type="text"
            inputMode="text"
            autoComplete="off"
            spellCheck={false}
            placeholder="XXXX-XXXX"
            maxLength={9}
            value={loginCode}
            onChange={e => setLoginCode(e.target.value)}
            autoFocus
          />

          <p className="text-xs text-rose mt-2.5 min-h-[1.1rem]">{loginError}</p>

          <button
            type="submit"
            className="btn btn-gold w-full mt-3 py-3!"
            disabled={loggingIn || loginCode.replace('-', '').length < 8}
          >
            {loggingIn ? 'Checking…' : 'Open the dashboard'}
          </button>
        </form>

        <p className="text-xs text-text-secondary mt-6 leading-relaxed">
          Operators can pair someone else with <span className="kbd">/colonyweb sync &lt;player&gt;</span>,
          and grant extra colonies with <span className="kbd">/colonyweb access grant</span>.
        </p>
      </div>
    </div>
  )
}
