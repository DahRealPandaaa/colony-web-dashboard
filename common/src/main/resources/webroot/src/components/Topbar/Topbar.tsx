import type { ColonySummary } from '../../types/colony'
import Refresh from '../icons/Refresh'
import SignOut from '../icons/SignOut'

const CONNECTION_LABEL: Record<string, string> = {
  live: 'Live',
  down: 'Reconnecting',
  connecting: 'Connecting',
}

interface Props {
  title: string
  subtitle: string
  connection: 'connecting' | 'live' | 'down'
  colonies: ColonySummary[]
  colonyId: number | null
  onColonyChange: (id: number) => void
  onRefresh: () => void
  /** The sidebar carries sign-out on wide screens, but it is hidden below lg. */
  showSignOut: boolean
  onSignOut: () => void
}

export function Topbar({
  title, subtitle, connection, colonies, colonyId, onColonyChange, onRefresh, showSignOut, onSignOut,
}: Props) {
  const chipClass = connection === 'live' ? 'good' : connection === 'down' ? 'bad' : ''
  const dotClass = connection === 'live' ? 'bg-emerald-400'
    : connection === 'down' ? 'bg-rose-500' : 'bg-amber-400'

  return (
    <header className="topbar">
      <div className="min-w-0 flex-1">
        <h1 className="page-title truncate">{title}</h1>
        <p className="page-sub truncate">{subtitle}</p>
      </div>

      <span className={`chip ${chipClass}`} title={`Live update stream: ${connection}`}>
        <i className={`w-1.5 h-1.5 rounded-full ${dotClass}`} />
        <span className="hidden sm:inline">{CONNECTION_LABEL[connection]}</span>
      </span>

      {colonies.length > 1 && (
        <select className="field font-semibold min-w-[150px] max-w-[220px]"
          value={colonyId ?? ''} onChange={e => onColonyChange(Number(e.target.value))}>
          {colonies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      )}
      {colonies.length === 1 && <span className="chip">{colonies[0].name}</span>}

      <button className="btn-icon" onClick={onRefresh} title="Refresh now">
        <Refresh />
      </button>

      {showSignOut && (
        <button className="btn-icon lg:hidden" onClick={onSignOut} title="Sign out">
          <SignOut size={16} />
        </button>
      )}
    </header>
  )
}
