import { AuthProvider, useAuth } from './contexts/AuthContext'
import { ColonyProvider } from './contexts/ColonyContext'
import { UiProvider } from './contexts/UiContext'
import { AppShell } from './pages/AppShell/AppShell'
import { LoginForm } from './pages/Login/Login'
import House from './components/icons/House'

function Dashboard() {
  const { authReady, signedIn } = useAuth()

  if (!authReady) {
    return (
      <div className="login-wrap">
        <div className="flex flex-col items-center gap-4">
          <span className="brand-mark w-11 h-11 animate-pulse-soft">
            <House size={24} />
          </span>
          <span className="text-sm text-slate-400">Connecting to the colony…</span>
        </div>
      </div>
    )
  }

  if (!signedIn) return <LoginForm />

  return (
    <ColonyProvider>
      <UiProvider>
        <AppShell />
      </UiProvider>
    </ColonyProvider>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <Dashboard />
    </AuthProvider>
  )
}
