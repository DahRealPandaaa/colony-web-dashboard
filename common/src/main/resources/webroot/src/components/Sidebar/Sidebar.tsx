import { useState } from 'react'
import { num } from '../../format/format'
import { colonyCount, type WebUser } from '../../types/auth'
import type { TabId } from '../../contexts/ColonyContext'
import House from '../icons/House'
import SignOut from '../icons/SignOut'

export interface TabDef {
  id: TabId
  label: string
  title: string
  subtitle: string
  /** Raw SVG path data, injected into a shared <svg> frame. */
  icon: string
}

/** The signed-in player's Minecraft head, with their initials as the fallback. */
function PlayerAvatar({ profile }: { profile: WebUser }) {
  const [failed, setFailed] = useState(false)

  if (!profile.uuid || failed) {
    return (
      <span className="w-8 h-8 shrink-0 grid place-items-center rounded-lg bg-white/[0.06] border border-line
                       text-[12px] font-bold uppercase text-accent-soft">
        {(profile.name || '?').slice(0, 2)}
      </span>
    )
  }

  return (
    <img
      className="w-8 h-8 shrink-0 rounded-lg border border-line"
      style={{ imageRendering: 'pixelated' }}
      src={`https://mc-heads.net/avatar/${encodeURIComponent(profile.uuid)}/32.png`}
      alt={profile.name ? `${profile.name}'s Minecraft head` : 'Minecraft player head'}
      onError={() => setFailed(true)}
    />
  )
}

interface Props {
  tabs: TabDef[]
  currentTab: TabId
  onTabChange: (id: TabId) => void
  profile: WebUser | null
  onSignOut: () => void
  tabCount: (id: TabId) => number | null
}

export function Sidebar({ tabs, currentTab, onTabChange, profile, onSignOut, tabCount }: Props) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark">
          <House />
        </span>
        <span className="min-w-0">
          <span className="brand-name block">ColonyWeb</span>
          <span className="brand-sub block">MineColonies</span>
        </span>
      </div>

      <nav className="flex flex-col gap-0.5">
        {tabs.map(t => {
          const count = tabCount(t.id)
          return (
            <button key={t.id} className={`nav-item${currentTab === t.id ? ' on' : ''}`}
              onClick={() => onTabChange(t.id)}>
              <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
                dangerouslySetInnerHTML={{ __html: t.icon }} />
              <span>{t.label}</span>
              {count !== null && <span className="nav-count">{num(count)}</span>}
            </button>
          )
        })}
      </nav>

      {/* Signed-in player, pinned to the bottom. */}
      {profile && (
        <div className="mt-auto pt-3 border-t border-line">
          <div className="flex items-center gap-2.5 px-1">
            <PlayerAvatar profile={profile} />
            <span className="flex-1 min-w-0">
              <span className="block text-[12.5px] font-semibold truncate">{profile.name}</span>
              <span className="block text-[12px] text-slate-400">
                {profile.admin ? 'Operator' : `${num(colonyCount(profile))} colonies`}
              </span>
            </span>
            <button className="btn-icon w-8! h-8!" onClick={onSignOut} title="Sign out">
              <SignOut />
            </button>
          </div>
        </div>
      )}
    </aside>
  )
}
