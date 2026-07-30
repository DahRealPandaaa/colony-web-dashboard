import { useCallback, useMemo, useRef, useState, type CSSProperties, type PointerEvent, type WheelEvent } from 'react'
import { useColony } from '../contexts/ColonyContext'
import { useUi } from '../contexts/UiContext'
import type { BuildingInfo } from '../types/building'
import type { CitizenInfo } from '../types/citizen'

/**
 * Map tab: a pannable, zoomable top-down view of the colony.
 *
 * The terrain layer is a PNG the server draws from the world at one pixel per block, so world
 * coordinates and image pixels are the same thing — a marker at block X sits at pixel
 * `X - minX`. Everything (terrain and markers alike) therefore lives inside one transformed
 * layer, and panning is a single CSS transform rather than a position recalculated per marker.
 * Markers counter-scale by `1 / zoom` so they stay a readable size at any magnification.
 */

const MIN_ZOOM = 0.2
const MAX_ZOOM = 8

/** Marker sizes in screen pixels — kept constant by counter-scaling against the zoom. */
export const BUILDING_MARKER = 26
export const CITIZEN_MARKER = 12
export const CENTRE_MARKER = 46

const clampZoom = (z: number) => Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z))

export function useMap() {
  const { colonyId, map, snap, citizens } = useColony()
  const {
    mapView, setMapView,
    showBuildings, setShowBuildings,
    showCitizens, setShowCitizens,
    showLabels, setShowLabels,
  } = useUi()

  const { zoom, panX, panZ, fitted } = mapView

  const [dragging, setDragging] = useState(false)
  const [hoverX, setHoverX] = useState<number | null>(null)
  const [hoverZ, setHoverZ] = useState<number | null>(null)
  const dragFrom = useRef({ x: 0, z: 0 })
  /** Set once a drag has actually moved, so releasing over a marker does not open it. */
  const dragMoved = useRef(false)

  const mapReady = !!(map && map.available && map.ready)

  const mappedPct = useMemo(() => {
    if (!map || !map.chunksTotal) return 0
    return Math.min(100, Math.round((map.chunksMapped / map.chunksTotal) * 100))
  }, [map])

  /** The terrain image, versioned so a redraw is never served from the browser cache. */
  const mapImageUrl = mapReady && colonyId != null ? `/map/${colonyId}.png?v=${map!.version}` : null

  // ---- view transform ----

  const mapTransform: CSSProperties = {
    transform: `translate(${panX}px, ${panZ}px) scale(${zoom})`,
  }

  /** Markers are placed in block space and un-scaled, so they keep their size. */
  const markerStyle = useCallback((x: number, z: number, size: number): CSSProperties => {
    if (!map) return { display: 'none' }
    return {
      left: x - map.minX,
      top: z - map.minZ,
      width: size,
      height: size,
      marginLeft: -size / 2,
      marginTop: -size / 2,
      transform: `scale(${1 / zoom})`,
    }
  }, [map, zoom])

  const buildingMarkerStyle = useCallback(
    (b: BuildingInfo) => markerStyle(b.x, b.z, BUILDING_MARKER), [markerStyle])
  const citizenMarkerStyle = useCallback(
    (c: CitizenInfo) => markerStyle(c.x, c.z, CITIZEN_MARKER), [markerStyle])
  const centreMarkerStyle = useCallback(
    () => (map ? markerStyle(map.centerX, map.centerZ, CENTRE_MARKER) : { display: 'none' as const }),
    [map, markerStyle])

  /** Frame the whole map in the stage. */
  const fitMap = useCallback((stage: HTMLElement | null) => {
    if (!stage || !map || !map.width) return
    const box = stage.getBoundingClientRect()
    if (!box.width || !box.height) return
    const fit = Math.min(box.width / map.width, box.height / map.height)
    const next = clampZoom(fit * 0.94)
    setMapView({
      zoom: next,
      panX: (box.width - map.width * next) / 2,
      panZ: (box.height - map.height * next) / 2,
      fitted: true,
    })
  }, [map, setMapView])

  /** Re-fit on first paint and whenever the footprint changed. */
  const maybeFitMap = useCallback((stage: HTMLElement | null) => {
    if (!fitted) fitMap(stage)
  }, [fitted, fitMap])

  /** Put the colony centre in the middle of the stage at the current zoom. */
  const centreMap = useCallback((stage: HTMLElement | null) => {
    if (!stage || !map) return
    const box = stage.getBoundingClientRect()
    setMapView(prev => ({
      ...prev,
      panX: box.width / 2 - (map.centerX - map.minX) * prev.zoom,
      panZ: box.height / 2 - (map.centerZ - map.minZ) * prev.zoom,
    }))
  }, [map, setMapView])

  /** Zoom about a point in stage coordinates, so what is under it stays put. */
  const zoomAt = useCallback((factor: number, stageX: number, stageZ: number) => {
    setMapView(prev => {
      const next = clampZoom(prev.zoom * factor)
      if (next === prev.zoom) return prev
      const ratio = next / prev.zoom
      return {
        ...prev,
        zoom: next,
        panX: stageX - (stageX - prev.panX) * ratio,
        panZ: stageZ - (stageZ - prev.panZ) * ratio,
      }
    })
  }, [setMapView])

  const zoomBy = useCallback((factor: number, stage: HTMLElement | null) => {
    if (!stage) return
    const box = stage.getBoundingClientRect()
    zoomAt(factor, box.width / 2, box.height / 2)
  }, [zoomAt])

  const onMapWheel = useCallback((event: WheelEvent, stage: HTMLElement | null) => {
    if (!stage) return
    const box = stage.getBoundingClientRect()
    zoomAt(event.deltaY < 0 ? 1.15 : 1 / 1.15, event.clientX - box.left, event.clientY - box.top)
  }, [zoomAt])

  // ---- pointer handling ----

  const onMapDown = useCallback((event: PointerEvent) => {
    setDragging(true)
    dragMoved.current = false
    dragFrom.current = { x: event.clientX - panX, z: event.clientY - panZ }
  }, [panX, panZ])

  const onMapMove = useCallback((event: PointerEvent, stage: HTMLElement | null) => {
    const clientX = event.clientX
    const clientY = event.clientY

    if (dragging) {
      const nextX = clientX - dragFrom.current.x
      const nextZ = clientY - dragFrom.current.z
      setMapView(prev => {
        if (Math.abs(nextX - prev.panX) + Math.abs(nextZ - prev.panZ) > 3) dragMoved.current = true
        return { ...prev, panX: nextX, panZ: nextZ }
      })
    }

    if (!map || !stage) return
    const box = stage.getBoundingClientRect()
    setHoverX(Math.floor((clientX - box.left - panX) / zoom) + map.minX)
    setHoverZ(Math.floor((clientY - box.top - panZ) / zoom) + map.minZ)
  }, [dragging, map, panX, panZ, zoom, setMapView])

  const onMapUp = useCallback(() => setDragging(false), [])

  const onMapLeave = useCallback(() => {
    setDragging(false)
    setHoverX(null)
    setHoverZ(null)
  }, [])

  /** A click that ended a pan must not also open the marker underneath it. */
  const clickedWithoutDrag = useCallback(() => !dragMoved.current, [])

  // ---- markers ----

  /** Buildings, with the ones being worked on drawn last so they sit on top. */
  const mapBuildings = useMemo(() => {
    if (!showBuildings) return []
    return (snap.buildings || []).slice()
      .sort((a, b) => (a.beingBuilt ? 1 : 0) - (b.beingBuilt ? 1 : 0))
  }, [showBuildings, snap.buildings])

  /** Only citizens the server could actually place — an unloaded citizen has no position. */
  const mapCitizens = useMemo(() => {
    if (!showCitizens) return []
    return (citizens || []).filter(c => c.spawned && Number.isFinite(c.x) && Number.isFinite(c.z))
  }, [showCitizens, citizens])

  /** Dot colour: guards, workers, children and the unemployed read differently. */
  const citizenDotClass = (c: CitizenInfo) => {
    if (!c.spawned) return 'asleep'
    if (c.child) return 'child'
    return c.jobType ? 'worker' : 'idle'
  }

  const citizenTitle = (c: CitizenInfo) => `${c.name} — ${c.job || 'Unemployed'} (${c.x}, ${c.z})`
  const buildingTitle = (b: BuildingInfo) => `${b.name} · level ${b.level} (${b.x}, ${b.z})`

  return {
    map, mapReady, mappedPct, mapImageUrl,
    zoom, panX, panZ, mapTransform, dragging, hoverX, hoverZ,
    showBuildings, setShowBuildings,
    showCitizens, setShowCitizens,
    showLabels, setShowLabels,
    fitMap, maybeFitMap, centreMap, zoomBy, onMapWheel,
    onMapDown, onMapMove, onMapUp, onMapLeave, clickedWithoutDrag,
    mapBuildings, mapCitizens,
    markerStyle, buildingMarkerStyle, citizenMarkerStyle, centreMarkerStyle,
    citizenDotClass, citizenTitle, buildingTitle,
  }
}
