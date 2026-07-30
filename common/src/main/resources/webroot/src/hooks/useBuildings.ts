import { useMemo } from 'react'
import { useColony } from '../contexts/ColonyContext'
import { useUi } from '../contexts/UiContext'
import { matches, statusOf } from '../format/format'
import { textureUrl } from '../api'
import type { BuildingInfo, WorkOrderInfo } from '../types/building'

/**
 * Buildings tab: the card grid, its filters, and the per-building detail modal.
 */
export function useBuildings() {
  const { snap, building, openBuilding, closeBuilding } = useColony()
  const {
    search, setSearch, sort, setSort,
    onlyInProgress, setOnlyInProgress,
    showDecorations, setShowDecorations,
    buildingSearch, setBuildingSearch,
  } = useUi()

  // ---- work-order lookups ----

  /** The work order for a building, via the index built when the snapshot loads. */
  const workOrder = (b: BuildingInfo): WorkOrderInfo | null =>
    snap.workOrdersById[b.workOrderId] ?? null

  const workOrderTargetLevel = (b: BuildingInfo) => workOrder(b)?.targetLevel ?? 0
  const actionOf = (b: BuildingInfo) => workOrder(b)?.action ?? null
  const buildingProgress = (b: BuildingInfo) => {
    const order = workOrder(b)
    return order ? Math.round((order.progress || 0) * 100) : 0
  }
  const builtBy = (b: BuildingInfo) => workOrder(b)?.builderName || null

  /** How many required resources are satisfied / deliverable / missing. */
  const resourceCounts = (b: BuildingInfo) => {
    const counts = { ok: 0, deliver: 0, missing: 0 }
    ;(b.required || []).forEach(r => { counts[statusOf(r)]++ })
    return counts
  }

  /** Prefer the real MineColonies hut block placed at the site. */
  const buildingIcon = (b: BuildingInfo) => {
    if (b.blockId) return textureUrl(b.blockId)
    if (b.kind === 'decoration') {
      const first = (b.required || [])[0]
      return textureUrl(first ? first.itemKey : 'minecolonies:blockhutbuilder')
    }
    const path = (b.type || '').split(':').pop()!.replace(/[^a-z0-9_]/g, '')
    return textureUrl('minecolonies:blockhut' + path)
  }

  // ---- list ----

  const visibleBuildings = useMemo(() => {
    let list = (snap.buildings || []).slice()
    if (!showDecorations) list = list.filter(b => b.kind !== 'decoration')
    if (onlyInProgress) list = list.filter(b => b.beingBuilt)

    const query = search.trim()
    if (query) {
      list = list.filter(b => matches(query, b.name)
        || (b.required || []).some(r => matches(query, r.name, r.material)))
    }

    const progressOf = (b: BuildingInfo) => {
      const order = snap.workOrdersById[b.workOrderId]
      return order ? Math.round((order.progress || 0) * 100) : 0
    }
    const missingOf = (b: BuildingInfo) =>
      (b.required || []).reduce((n, r) => n + (statusOf(r) === 'missing' ? 1 : 0), 0)

    return list.sort((a, b) => {
      if (sort === 'name') return (a.name || '').localeCompare(b.name || '')
      if (sort === 'progress') return progressOf(b) - progressOf(a)
      if (sort === 'level') return b.level - a.level
      // "status": in-progress first, then whatever is missing the most.
      const inProgress = (b.beingBuilt ? 1 : 0) - (a.beingBuilt ? 1 : 0)
      if (inProgress !== 0) return inProgress
      return missingOf(b) - missingOf(a)
    })
  }, [snap.buildings, snap.workOrdersById, showDecorations, onlyInProgress, search, sort])

  // ---- detail modal ----

  /** Requirements for the open building, searched and sorted missing-first. */
  const buildingResources = useMemo(() => {
    if (!building) return []
    let list = (building.required || []).slice()
    const query = buildingSearch.trim()
    if (query) list = list.filter(r => matches(query, r.name, r.material))

    const rank = { missing: 0, deliver: 1, ok: 2 }
    return list.sort((a, b) =>
      (rank[statusOf(a)] - rank[statusOf(b)]) || (b.needed - a.needed))
  }, [building, buildingSearch])

  return {
    search, setSearch, sort, setSort,
    onlyInProgress, setOnlyInProgress,
    showDecorations, setShowDecorations,
    building, openBuilding, closeBuilding,
    buildingSearch, setBuildingSearch,
    visibleBuildings, buildingResources,
    workOrder, workOrderTargetLevel, actionOf, buildingProgress, builtBy,
    resourceCounts, buildingIcon,
  }
}
