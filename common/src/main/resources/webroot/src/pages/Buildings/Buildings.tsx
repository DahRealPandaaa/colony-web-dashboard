import { useColony } from '../../contexts/ColonyContext'
import { useBuildings } from '../../hooks/useBuildings'
import type { BuildingSort } from '../../contexts/UiContext'
import BuildingCard from '../../components/BuildingCard/BuildingCard'
import SearchInput from '../../components/SearchInput/SearchInput'
import ToggleSwitch from '../../components/ToggleSwitch/ToggleSwitch'

export function BuildingsTab() {
  const { snap } = useColony()
  const b = useBuildings()

  return (
    <div className="animate-fade-up">
      <div className="toolbar">
        <SearchInput
          className="flex-1 min-w-[200px]"
          value={b.search}
          onChange={b.setSearch}
          placeholder="Search buildings, items or materials…"
        />
        <ToggleSwitch label="In progress only" checked={b.onlyInProgress} onChange={b.setOnlyInProgress} />
        <ToggleSwitch label="Decorations" checked={b.showDecorations} onChange={b.setShowDecorations} />
        <select className="field py-1.5" aria-label="Sort buildings"
          value={b.sort} onChange={e => b.setSort(e.target.value as BuildingSort)}>
          <option value="status">Missing first</option>
          <option value="progress">Progress</option>
          <option value="name">Name</option>
          <option value="level">Level</option>
        </select>
        <span className="chip on ml-auto">{b.visibleBuildings.length} shown</span>
      </div>

      <div className="grid grid-auto-cards gap-3.5">
        {b.visibleBuildings.map(building => (
          <BuildingCard
            key={building.id}
            building={building}
            targetLevel={b.workOrderTargetLevel(building)}
            progress={b.buildingProgress(building)}
            builtBy={b.builtBy(building)}
            counts={b.resourceCounts(building)}
            onOpen={() => b.openBuilding(building)}
          />
        ))}
      </div>

      {!b.visibleBuildings.length && (
        <div className="empty">
          <p className="empty-title">
            {snap.buildings.length
              ? 'No buildings match your filters.'
              : 'No buildings found in this colony.'}
          </p>
        </div>
      )}
    </div>
  )
}
