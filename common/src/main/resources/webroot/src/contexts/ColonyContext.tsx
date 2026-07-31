import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState,
  type ReactNode,
} from 'react'
import {
  Unauthorized, fetchCitizenDetail, fetchColonies, fetchColonySection, fetchColonySnapshot,
} from '../api'
import { useAuth } from './AuthContext'
import type { ColonySnapshot, ColonyStats, ColonySummary } from '../types/colony'
import type { BuildingInfo } from '../types/building'
import type { CitizenInfo, EquipmentInfo } from '../types/citizen'
import type { ItemCount } from '../types/item'
import type { ResearchInfo } from '../types/research'
import type { CombatInfo } from '../types/combat'
import type { MapInfo } from '../types/map'

/** How often to ask the server how far the map has got, while the tab is open. */
const MAP_POLL_MS = 2500

export const TAB_IDS = ['overview', 'map', 'buildings', 'citizens', 'research', 'combat', 'warehouse'] as const
export type TabId = typeof TAB_IDS[number]

/** Which lazily-loaded sections each tab needs. */
const TAB_SECTIONS: Record<string, string[]> = {
  // The map plots the citizen roster, so it needs that section as well as its own.
  map: ['map', 'citizens'],
  citizens: ['citizens'],
  research: ['research'],
  combat: ['combat'],
}

type SectionName = 'citizens' | 'research' | 'combat' | 'map'
type LoadedSections = Record<SectionName, boolean>

/** Zeroed stats so the overview renders before the first snapshot arrives. */
function emptyStats(): ColonyStats {
  return {
    citizens: 0, maxCitizens: 0, children: 0, unemployed: 0,
    happiness: 0, saturation: 0,
    buildings: 0, decorations: 0, workOrders: 0, builders: 0, guards: 0,
    warehouseTypes: 0, warehouseItems: 0,
    researchCompleted: 0, researchInProgress: 0,
    raided: false, nightsSinceRaid: 0,
  }
}

function emptySnapshot(): ColonySnapshot {
  return {
    id: 0, name: '', dimension: '', owner: '',
    builders: [], workOrders: [], buildings: [],
    warehouse: { present: false, stacks: [] },
    stats: emptyStats(),
    workOrdersById: {},
  }
}

function noSections(): LoadedSections {
  return { citizens: false, research: false, combat: false, map: false }
}

export interface ColonyState {
  colonies: ColonySummary[]
  colonyId: number | null
  colony: ColonySummary | null
  snap: ColonySnapshot
  stats: ColonyStats
  citizens: CitizenInfo[]
  research: ResearchInfo | null
  combat: CombatInfo | null
  map: MapInfo | null
  loaded: LoadedSections
  connection: 'connecting' | 'live' | 'down'

  tab: TabId
  setTab: (id: TabId) => void
  tabCount: (id: TabId) => number | null

  selectColony: (id: number) => void
  refresh: () => Promise<void>

  /** The building whose detail modal is open, if any. */
  building: BuildingInfo | null
  openBuilding: (building: BuildingInfo) => void
  closeBuilding: () => void

  /** The citizen whose detail modal is open, plus their on-demand inventory. */
  citizen: CitizenInfo | null
  citizenInventory: ItemCount[]
  citizenEquipment: EquipmentInfo[]
  openCitizen: (citizen: CitizenInfo) => void
  closeCitizen: () => void
}

const ColonyContext = createContext<ColonyState>(null!)

/** Read `#<colonyId>/<tab>` out of the address bar. */
function readHash(): { colonyId: number | null; tab: TabId | null } {
  const [rawId, rawTab] = location.hash.replace('#', '').split('/')
  const id = parseInt(rawId, 10)
  const tab = (TAB_IDS as readonly string[]).includes(rawTab) ? (rawTab as TabId) : null
  return { colonyId: isNaN(id) ? null : id, tab }
}

/** Keeps a ref in step with a value, so stable callbacks can read the latest one. */
function useLatest<T>(value: T) {
  const ref = useRef(value)
  // Sync happens in an effect, not during render: ref writes in the render
  // body are visible side effects that StrictMode double-invocation exposes.
  useEffect(() => { ref.current = value }, [value])
  return ref
}

export function ColonyProvider({ children }: { children: ReactNode }) {
  const { onUnauthorized } = useAuth()
  const initial = useRef(readHash()).current

  const [colonies, setColonies] = useState<ColonySummary[]>([])
  const [colonyId, setColonyId] = useState<number | null>(initial.colonyId)
  const [snap, setSnap] = useState<ColonySnapshot>(emptySnapshot)
  const [citizens, setCitizens] = useState<CitizenInfo[]>([])
  const [research, setResearch] = useState<ResearchInfo | null>(null)
  const [combat, setCombat] = useState<CombatInfo | null>(null)
  const [map, setMap] = useState<MapInfo | null>(null)
  const [loaded, setLoaded] = useState<LoadedSections>(noSections)
  const [tab, setTabState] = useState<TabId>(initial.tab ?? 'overview')
  const [connection, setConnection] = useState<'connecting' | 'live' | 'down'>('connecting')

  const [building, setBuilding] = useState<BuildingInfo | null>(null)
  const [citizen, setCitizen] = useState<CitizenInfo | null>(null)
  const [citizenInventory, setCitizenInventory] = useState<ItemCount[]>([])
  const [citizenEquipment, setCitizenEquipment] = useState<EquipmentInfo[]>([])

  const colonyIdRef = useLatest(colonyId)
  const tabRef = useLatest(tab)
  const loadedRef = useLatest(loaded)
  const buildingRef = useLatest(building)
  const citizenRef = useLatest(citizen)
  const onUnauthorizedRef = useLatest(onUnauthorized)

  /** Run a request, bouncing to the sign-in screen if the session has gone. */
  const guarded = useCallback(async <T,>(work: () => Promise<T>): Promise<T | null> => {
    try {
      return await work()
    } catch (e) {
      if (e instanceof Unauthorized) onUnauthorizedRef.current()
      else console.error(e)
      return null
    }
  }, [onUnauthorizedRef])

  // ---- loading ----

  const loadSection = useCallback(async (section: SectionName) => {
    const id = colonyIdRef.current
    if (id == null) return
    await guarded(async () => {
      const data = await fetchColonySection<unknown>(id, section)
      // A late reply for a colony we have since left must not overwrite the new one.
      if (colonyIdRef.current !== id) return
      if (section === 'citizens') setCitizens(data as CitizenInfo[])
      else if (section === 'research') setResearch(data as ResearchInfo)
      else if (section === 'combat') setCombat(data as CombatInfo)
      else if (section === 'map') setMap(data as MapInfo)
      setLoaded(prev => ({ ...prev, [section]: true }))
    })
  }, [guarded, colonyIdRef])

  /** Keep the open building modal in sync when a live update replaces the snapshot. */
  const refreshOpenBuilding = useCallback((snapshot: ColonySnapshot) => {
    const open = buildingRef.current
    if (!open) return
    setBuilding(snapshot.buildings.find(b => b.id === open.id) ?? null)
  }, [buildingRef])

  const loadSnapshot = useCallback(async () => {
    const id = colonyIdRef.current
    if (id == null) return
    await guarded(async () => {
      const snapshot = await fetchColonySnapshot(id)
      if (colonyIdRef.current !== id) return
      // Index work orders once per refresh, so rendering never scans the list.
      snapshot.workOrdersById = {}
      ;(snapshot.workOrders || []).forEach(w => { snapshot.workOrdersById[w.id] = w })
      setSnap(snapshot)
      refreshOpenBuilding(snapshot)
    })
  }, [guarded, colonyIdRef, refreshOpenBuilding])

  /**
   * Load the sections the current tab needs. Already-loaded sections are only re-fetched when
   * `force` is set — i.e. the colony data actually changed.
   */
  const ensureSections = useCallback(async (force: boolean) => {
    for (const section of TAB_SECTIONS[tabRef.current] || []) {
      if (loadedRef.current[section as SectionName] && !force) continue
      await loadSection(section as SectionName)
    }
  }, [loadSection, tabRef, loadedRef])

  const loadCitizenDetail = useCallback(async () => {
    const open = citizenRef.current
    const id = colonyIdRef.current
    if (!open || id == null) return
    await guarded(async () => {
      const data = await fetchCitizenDetail(id, open.id)
      if (citizenRef.current?.id !== open.id) return
      setCitizen(data.citizen)
      setCitizenInventory(data.inventory || [])
      setCitizenEquipment(data.equipment || [])
    })
  }, [guarded, citizenRef, colonyIdRef])

  /** Reload the snapshot plus whatever the visible tab needs. */
  const refresh = useCallback(async () => {
    await loadSnapshot()
    await ensureSections(true)
    if (citizenRef.current) await loadCitizenDetail()
  }, [loadSnapshot, ensureSections, loadCitizenDetail, citizenRef])

  const loadColonies = useCallback(async () => {
    await guarded(async () => {
      const list = await fetchColonies()
      setColonies(list)
      const current = colonyIdRef.current
      if (!list.length) {
        setColonyId(null)
      } else if (current == null || !list.some(c => c.id === current)) {
        setColonyId(list[0].id)
      }
    })
  }, [guarded, colonyIdRef])

  // ---- colony + tab routing ----

  const selectColony = useCallback((id: number) => {
    setColonyId(id)
    setSnap(emptySnapshot())
    setCitizens([])
    setResearch(null)
    setCombat(null)
    setMap(null)
    setLoaded(noSections())
    setBuilding(null)
    setCitizen(null)
    setCitizenInventory([])
    setCitizenEquipment([])
  }, [])

  const setTab = useCallback((id: TabId) => setTabState(id), [])

  const stats = snap.stats || emptyStats()

  const tabCount = useCallback((id: TabId): number | null => {
    switch (id) {
      case 'buildings': return snap.buildings.length
      case 'warehouse': return snap.warehouse.stacks.length
      case 'citizens': return loaded.citizens ? citizens.length : stats.citizens
      case 'research': return research ? research.completed : null
      case 'combat': return combat ? combat.guardCount : stats.guards
      default: return null
    }
  }, [snap, loaded.citizens, citizens.length, stats, research, combat])

  // ---- modal selection ----

  const openBuilding = useCallback((b: BuildingInfo) => setBuilding(b), [])
  const closeBuilding = useCallback(() => setBuilding(null), [])

  const openCitizen = useCallback((c: CitizenInfo) => {
    setCitizen(c)
    setCitizenInventory([])
    setCitizenEquipment([])
  }, [])

  const closeCitizen = useCallback(() => {
    setCitizen(null)
    setCitizenInventory([])
    setCitizenEquipment([])
  }, [])

  // Inventories are fetched on demand, so the roster stays small.
  useEffect(() => {
    if (citizen) loadCitizenDetail()
    // Only when a different citizen is opened, not on every detail refresh.
  }, [citizen?.id])

  // ---- effects ----

  useEffect(() => { loadColonies() }, [loadColonies])

  // A colony was picked (or swapped): pull its snapshot.
  useEffect(() => {
    if (colonyId != null) loadSnapshot()
  }, [colonyId, loadSnapshot])

  // Whatever the visible tab needs, once.
  useEffect(() => {
    if (colonyId != null) ensureSections(false)
  }, [colonyId, tab, ensureSections])

  // Mirror the selection into the address bar, so a reload lands in the same place.
  useEffect(() => {
    if (colonyId != null) location.hash = `${colonyId}/${tab}`
  }, [colonyId, tab])

  // Follow the back/forward buttons.
  useEffect(() => {
    const onHashChange = () => {
      const next = readHash()
      if (next.tab) setTabState(next.tab)
      if (next.colonyId != null && next.colonyId !== colonyIdRef.current) selectColony(next.colonyId)
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [selectColony, colonyIdRef])

  // Live updates.
  useEffect(() => {
    const source = new EventSource('/events')
    source.addEventListener('open', () => setConnection('live'))
    source.addEventListener('error', () => setConnection('down'))
    source.addEventListener('update', (event: MessageEvent) => {
      let payload: { type?: string; id?: number }
      try {
        payload = JSON.parse(event.data)
      } catch {
        return
      }
      if (payload.type === 'colonies') loadColonies()
      else if (payload.type === 'colony' && payload.id === colonyIdRef.current) refresh()
    })
    return () => {
      source.close()
      setConnection('connecting')
    }
  }, [loadColonies, refresh, colonyIdRef])

  /**
   * Poll the map document while the tab is open.
   *
   * Colony data arrives over SSE, but the map fills in on the server's own schedule and would
   * otherwise only refresh when something else about the colony happened to change.
   */
  useEffect(() => {
    if (tab !== 'map' || colonyId == null) return
    const timer = setInterval(() => loadSection('map'), MAP_POLL_MS)
    return () => clearInterval(timer)
  }, [tab, colonyId, loadSection])

  const colony = useMemo(
    () => colonies.find(c => c.id === colonyId) ?? null,
    [colonies, colonyId],
  )

  const value = useMemo(() => ({
    colonies, colonyId, colony, snap, stats, citizens, research, combat, map, loaded, connection,
    tab, setTab, tabCount,
    selectColony, refresh,
    building, openBuilding, closeBuilding,
    citizen, citizenInventory, citizenEquipment, openCitizen, closeCitizen,
  }), [
    colonies, colonyId, colony, snap, stats, citizens, research, combat, map, loaded, connection,
    tab, setTab, tabCount,
    selectColony, refresh,
    building, openBuilding, closeBuilding,
    citizen, citizenInventory, citizenEquipment, openCitizen, closeCitizen,
  ])

  return (
    <ColonyContext.Provider value={value}>
      {children}
    </ColonyContext.Provider>
  )
}

export function useColony() {
  const ctx = useContext(ColonyContext)
  if (!ctx) throw new Error('useColony must be used within ColonyProvider')
  return ctx
}
