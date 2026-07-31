import {
  createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode,
} from 'react'
import { useColony } from './ColonyContext'

/**
 * View state that outlives a tab switch.
 *
 * Each tab's filters and the map's pan/zoom used to live on the one shared dashboard object,
 * so leaving a tab and coming back kept your search, sort and viewport. Tab components mount
 * and unmount, so that state is held here instead — above them, next to the colony data.
 */

export type BuildingSort = 'status' | 'progress' | 'name' | 'level'
export type CitizenSort = 'job' | 'name' | 'skills' | 'happiness' | 'health'
export type WarehouseSort = 'count' | 'alpha'
export type NeededSort = 'shortfall' | 'alpha'
export type ResearchFilter = 'all' | 'IN_PROGRESS' | 'COMPLETED' | 'NOT_STARTED'

export interface MapView {
  zoom: number
  panX: number
  panZ: number
  /** Cleared when the map first appears or its footprint changes, to trigger a re-fit. */
  fitted: boolean
}

interface UiState {
  search: string
  setSearch: (v: string) => void
  sort: BuildingSort
  setSort: (v: BuildingSort) => void
  onlyInProgress: boolean
  setOnlyInProgress: (v: boolean) => void
  showDecorations: boolean
  setShowDecorations: (v: boolean) => void
  buildingSearch: string
  setBuildingSearch: (v: string) => void

  citizenSearch: string
  setCitizenSearch: (v: string) => void
  citizenJob: string
  setCitizenJob: (v: string) => void
  citizenSort: CitizenSort
  setCitizenSort: (v: CitizenSort) => void

  researchBranch: string
  setResearchBranch: (v: string) => void
  researchFilter: ResearchFilter
  setResearchFilter: (v: ResearchFilter) => void

  whSearch: string
  setWhSearch: (v: string) => void
  whSort: WarehouseSort
  setWhSort: (v: WarehouseSort) => void

  neededSearch: string
  setNeededSearch: (v: string) => void
  neededSort: NeededSort
  setNeededSort: (v: NeededSort) => void

  mapView: MapView
  setMapView: (next: MapView | ((prev: MapView) => MapView)) => void
  showBuildings: boolean
  setShowBuildings: (v: boolean) => void
  showCitizens: boolean
  setShowCitizens: (v: boolean) => void
  showLabels: boolean
  setShowLabels: (v: boolean) => void
}

const DEFAULT_MAP_VIEW: MapView = { zoom: 1, panX: 0, panZ: 0, fitted: false }

const UiContext = createContext<UiState | null>(null)

export function UiProvider({ children }: { children: ReactNode }) {
  const { colonyId, map } = useColony()

  const [search, setSearch] = useState('')
  const [sort, setSort] = useState<BuildingSort>('status')
  const [onlyInProgress, setOnlyInProgress] = useState(false)
  const [showDecorations, setShowDecorations] = useState(true)
  const [buildingSearch, setBuildingSearch] = useState('')

  const [citizenSearch, setCitizenSearch] = useState('')
  const [citizenJob, setCitizenJob] = useState('')
  const [citizenSort, setCitizenSort] = useState<CitizenSort>('job')

  const [researchBranch, setResearchBranch] = useState('')
  const [researchFilter, setResearchFilter] = useState<ResearchFilter>('all')

  const [whSearch, setWhSearch] = useState('')
  const [whSort, setWhSort] = useState<WarehouseSort>('count')

  const [neededSearch, setNeededSearch] = useState('')
  const [neededSort, setNeededSort] = useState<NeededSort>('shortfall')

  const [mapView, setMapView] = useState<MapView>(DEFAULT_MAP_VIEW)
  const [showBuildings, setShowBuildings] = useState(true)
  const [showCitizens, setShowCitizens] = useState(true)
  const [showLabels, setShowLabels] = useState(false)

  // A new colony starts framed on its own centre, not the last one's viewport.
  useEffect(() => {
    setMapView(DEFAULT_MAP_VIEW)
    setResearchBranch('')
  }, [colonyId])

  // Re-fit whenever the map first appears or its footprint changes under us.
  const footprint = map ? `${map.minX},${map.minZ},${map.width},${map.height}` : null
  useEffect(() => {
    setMapView(prev => (prev.fitted ? { ...prev, fitted: false } : prev))
  }, [footprint])

  const value = useMemo<UiState>(() => ({
    search, setSearch, sort, setSort, onlyInProgress, setOnlyInProgress,
    showDecorations, setShowDecorations, buildingSearch, setBuildingSearch,
    citizenSearch, setCitizenSearch, citizenJob, setCitizenJob, citizenSort, setCitizenSort,
    researchBranch, setResearchBranch, researchFilter, setResearchFilter,
    whSearch, setWhSearch, whSort, setWhSort,
    neededSearch, setNeededSearch, neededSort, setNeededSort,
    mapView, setMapView, showBuildings, setShowBuildings,
    showCitizens, setShowCitizens, showLabels, setShowLabels,
  }), [
    search, sort, onlyInProgress, showDecorations, buildingSearch,
    citizenSearch, citizenJob, citizenSort,
    researchBranch, researchFilter, whSearch, whSort,
    neededSearch, neededSort,
    mapView, showBuildings, showCitizens, showLabels,
  ])

  return <UiContext.Provider value={value}>{children}</UiContext.Provider>
}

export function useUi() {
  const ctx = useContext(UiContext)
  if (!ctx) throw new Error('useUi must be used within UiProvider')
  return ctx
}

/** Reset the buildings modal search whenever a different building is opened. */
export function useResetBuildingSearch(buildingId: number | undefined) {
  const { setBuildingSearch } = useUi()
  useEffect(() => { setBuildingSearch('') }, [buildingId, setBuildingSearch])
}

export { DEFAULT_MAP_VIEW }
