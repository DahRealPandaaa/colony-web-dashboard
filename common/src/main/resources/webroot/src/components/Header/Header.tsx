import { useState } from 'react'
import type { ColonySummary } from '../../types/colony'
import type { WebUser } from '../../types/auth'
import { useTheme } from '../../contexts/ThemeContext'
import House from '../icons/House'
import Refresh from '../icons/Refresh'
import SignOut from '../icons/SignOut'
import Sun from '../icons/Sun'
import Moon from '../icons/Moon'

const CONNECTION_LABEL: Record<string, string> = {
  live: 'Live',
  down: 'Reconnecting',
  connecting: 'Connecting',
}

interface Props {
  connection: 'connecting' | 'live' | 'down'
  colonies: ColonySummary[]
  colonyId: number | null
  onColonyChange: (id: number) => void
  onRefresh: () => void
  profile: WebUser | null
  onSignOut: () => void
}

function PlayerAvatar({ profile }: { profile: WebUser }) {
  const [failed, setFailed] = useState(false)
  if (!profile.uuid || failed) {
    return (
      <span className="w-7 h-7 shrink-0 grid place-items-center rounded-md bg-white/[0.06] border border-line
                       text-xs font-bold uppercase text-accent-soft">
        {(profile.name || '?').slice(0, 2)}
      </span>
    )
  }
  return (
    <img
      className="w-7 h-7 shrink-0 rounded-md border border-line"
      style={{ imageRendering: 'pixelated' }}
      src={`https://mc-heads.net/avatar/${encodeURIComponent(profile.uuid)}/32.png`}
      alt={profile.name ? `${profile.name}'s Minecraft head` : 'Minecraft player head'}
      onError={() => setFailed(true)}
    />
  )
}

export function Header({
  connection, colonies, colonyId, onColonyChange, onRefresh,
  profile, onSignOut,
}: Props) {
  const { theme, toggleTheme } = useTheme()
  const chipClass = connection === 'live' ? 'good' : connection === 'down' ? 'bad' : ''
  const dotClass = connection === 'live' ? 'bg-emerald-400'
    : connection === 'down' ? 'bg-rose-500' : 'bg-amber-400'

  return (
    <header className="app-header">
      {/* Brand */}
      <div className="flex items-center gap-2.5 shrink-0">
        <span className="brand-mark">
          <House size={18} />
        </span>
        <span className="hidden sm:block min-w-0 leading-tight">
          <span className="text-[15px] font-bold tracking-wide block">ColonyWeb</span>
          <span className="text-[10px] font-semibold tracking-[1.2px] text-text-muted block">MINECOLONIES</span>
        </span>
      </div>

      <span className="flex-1" />

      {/* Right controls */}
      <div className="flex items-center gap-1.5 shrink-0">
        {colonies.length > 1 ? (
          <select className="field font-semibold max-w-[160px] py-1.5 text-xs"
            value={colonyId ?? ''} onChange={e => onColonyChange(Number(e.target.value))}>
            {colonies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        ) : colonies.length === 1 ? (
          <span className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-ink-850 border border-line text-[13px] font-semibold">
            {colonies[0].name}
          </span>
        ) : null}

        <span className={`chip ${chipClass} text-xs`} title={`Live update stream: ${connection}`}>
          <i className={`w-1.5 h-1.5 rounded-full ${dotClass}`} />
          <span className="hidden sm:inline">{CONNECTION_LABEL[connection]}</span>
        </span>

        <button className="btn-icon" onClick={onRefresh} title="Refresh now">
          <Refresh size={15} />
        </button>

        <button className="btn-icon" onClick={toggleTheme}
          title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
          {theme === 'dark' ? <Sun size={15} /> : <Moon size={15} />}
        </button>

        {profile && (
          <div className="flex items-center gap-1.5 pl-1">
            <PlayerAvatar profile={profile} />
            <span className="hidden md:block text-xs font-semibold truncate max-w-[80px]">
              {profile.name}
            </span>
            <button className="btn-icon w-8! h-8!" onClick={onSignOut} title="Sign out">
              <SignOut size={14} />
            </button>
          </div>
        )}
      </div>
    </header>
  )
}
