import { useColony } from '../../contexts/ColonyContext'
import { useAuth } from '../../contexts/AuthContext'
import { Header } from '../../components/Header/Header'
import BuildingModal from '../../components/BuildingModal/BuildingModal'
import CitizenModal from '../../components/CitizenModal/CitizenModal'
import { OverviewTab } from '../Overview/Overview'
import { MapTab } from '../Map/Map'
import { BuildingsTab } from '../Buildings/Buildings'
import { NeededTab } from '../Needed/Needed'
import { CitizensTab } from '../Citizens/Citizens'
import { ResearchTab } from '../Research/Research'
import { CombatTab } from '../Combat/Combat'
import { WarehouseTab } from '../Warehouse/Warehouse'
import { TABS } from '../../navigation'
import { num } from '../../format/format'

export function AppShell() {
  const {
    tab, setTab, tabCount, colonies, colonyId, selectColony, connection, refresh,
    building, citizen,
  } = useColony()
  const { profile, signOut } = useAuth()

  const currentTab = TABS.find(t => t.id === tab) ?? TABS[0]

  return (
    <>
      <div className="app-shell">
        <Header
          connection={connection}
          colonies={colonies}
          colonyId={colonyId}
          onColonyChange={selectColony}
          onRefresh={refresh}
          profile={profile}
          onSignOut={signOut}
        />

        {/* Pill tab bar */}
        <nav className="tab-pill-bar" role="navigation" aria-label="Main navigation">
          {TABS.map(t => {
            const count = tabCount(t.id)
            const isActive = tab === t.id
            return (
              <button
                key={t.id}
                className={`tab-pill${isActive ? ' on' : ''}`}
                onClick={() => setTab(t.id)}
                aria-current={isActive ? 'page' : undefined}
              >
                <span className="tab-pill-icon">{t.iconEmoji}</span>
                <span className="tab-pill-label">{t.label}</span>
                {count !== null && <span className="tab-pill-badge">{num(count)}</span>}
              </button>
            )
          })}
        </nav>

        <main className="content" data-tint={currentTab.tint}>
          {!colonies.length ? (
            <div className="empty">
              <p className="empty-title">No colonies to show.</p>
              <p className="mt-1">
                Run <span className="kbd">/colonyweb sync</span> in-game after joining a colony.
              </p>
            </div>
          ) : (
            <>
              {tab === 'overview' && <OverviewTab />}
              {tab === 'map' && <MapTab />}
              {tab === 'buildings' && <BuildingsTab />}
              {tab === 'needed' && <NeededTab />}
              {tab === 'citizens' && <CitizensTab />}
              {tab === 'research' && <ResearchTab />}
              {tab === 'combat' && <CombatTab />}
              {tab === 'warehouse' && <WarehouseTab />}
            </>
          )}
        </main>
      </div>

      {building && <BuildingModal />}
      {citizen && <CitizenModal />}
    </>
  )
}
