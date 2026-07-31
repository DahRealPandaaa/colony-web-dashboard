import { useColony } from '../../contexts/ColonyContext'
import { useBuildings } from '../../hooks/useBuildings'
import type { BuildingSort } from '../../contexts/UiContext'
import BuildingCard from '../../components/BuildingCard/BuildingCard'
import Panel from '../../components/Panel/Panel'
import SearchInput from '../../components/SearchInput/SearchInput'
import ToggleSwitch from '../../components/ToggleSwitch/ToggleSwitch'
import { num } from '../../format/format'

export function BuildingsTab() {
  const { snap } = useColony()
  const b = useBuildings()

  const inProgress = b.visibleBuildings.filter(x => x.beingBuilt)
  const complete = b.visibleBuildings.filter(x => !x.beingBuilt)

  return (
    <div className="animate-fade-up flex flex-col gap-4">
      {/* Toolbar */}
      <div className="toolbar">
        <SearchInput className="flex-1 min-w-[180px]" value={b.search} onChange={b.setSearch}
          placeholder="Search buildings, items or materials…" />
        <ToggleSwitch label="In progress only" checked={b.onlyInProgress} onChange={b.setOnlyInProgress} />
        <ToggleSwitch label="Decorations" checked={b.showDecorations} onChange={b.setShowDecorations} />
        <select className="field py-1.5" aria-label="Sort buildings"
          value={b.sort} onChange={e => b.setSort(e.target.value as BuildingSort)}>
          <option value="status">Missing first</option>
          <option value="progress">Progress</option>
          <option value="name">Name</option>
          <option value="level">Level</option>
        </select>
        <span className="chip on">{b.visibleBuildings.length} shown</span>
      </div>

      {/* In progress */}
      {inProgress.length > 0 && (
        <Panel title="In progress" subtitle={`${inProgress.length} being built`}>
          <div className="grid grid-auto-cards gap-3">
            {inProgress.map(building => (
              <BuildingCard
                key={building.id} building={building} targetLevel={b.workOrderTargetLevel(building)}
                progress={b.buildingProgress(building)} builtBy={b.builtBy(building)}
                counts={b.resourceCounts(building)} onOpen={() => b.openBuilding(building)}
              />
            ))}
          </div>
        </Panel>
      )}

      {/* Complete */}
      {complete.length > 0 && (
        <Panel title="Complete" subtitle={`${complete.length} finished`} count={complete.length}>
          <div className="grid grid-auto-cards gap-3">
            {complete.map(building => (
              <BuildingCard
                key={building.id} building={building} targetLevel={b.workOrderTargetLevel(building)}
                progress={b.buildingProgress(building)} builtBy={b.builtBy(building)}
                counts={b.resourceCounts(building)} onOpen={() => b.openBuilding(building)}
              />
            ))}
          </div>
        </Panel>
      )}

      {!b.visibleBuildings.length && (
        <div className="empty">
          <p className="empty-title">
            {snap.buildings.length ? 'No buildings match your filters.' : 'No buildings found in this colony.'}
          </p>
        </div>
      )}
    </div>
  )
}
