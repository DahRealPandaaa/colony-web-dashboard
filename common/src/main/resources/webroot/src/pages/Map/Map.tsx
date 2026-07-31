import { useRef } from 'react'
import { useColony } from '../../contexts/ColonyContext'
import { useMap } from '../../hooks/useMap'
import { buildingArt, buildingIcon, buildingIconFallback } from '../../hooks/useIcons'
import MapLegend from '../../components/MapLegend/MapLegend'
import ToggleSwitch from '../../components/ToggleSwitch/ToggleSwitch'

export function MapTab() {
  const { openBuilding, openCitizen } = useColony()
  const stageRef = useRef<HTMLDivElement>(null)
  const m = useMap()

  const stage = () => stageRef.current

  return (
    <div className="animate-fade-up">
      <div className="toolbar">
        <ToggleSwitch label="Buildings" checked={m.showBuildings} onChange={m.setShowBuildings} />
        <ToggleSwitch label="Citizens" checked={m.showCitizens} onChange={m.setShowCitizens} />
        <ToggleSwitch label="Labels" checked={m.showLabels} onChange={m.setShowLabels} />

        <button type="button" className="chip chip-btn" onClick={() => m.fitMap(stage())}>Fit</button>
        <button type="button" className="chip chip-btn" onClick={() => m.centreMap(stage())}>Town hall</button>

        {m.map?.available && (
          <span
            className={`chip${m.mappedPct >= 100 ? ' good' : ''}`}
            title={`${m.map.chunksMapped} of ${m.map.chunksTotal} chunks drawn`}
          >
            {m.mappedPct >= 100 ? 'Fully mapped' : `Mapping ${m.mappedPct}%`}
          </span>
        )}

        <span className="chip ml-auto tabular-nums">
          {m.hoverX === null
            ? `Zoom ${Math.round(m.zoom * 100)}%`
            : `X ${m.hoverX} · Z ${m.hoverZ}`}
        </span>
      </div>

      <div
        className={`map-frame${m.dragging ? ' grabbing' : ''}`}
        ref={stageRef}
        onWheel={e => { e.preventDefault(); m.onMapWheel(e, stage()) }}
        onPointerDown={m.onMapDown}
        onPointerMove={e => m.onMapMove(e, stage())}
        onPointerUp={m.onMapUp}
        onPointerCancel={m.onMapUp}
        onPointerLeave={m.onMapLeave}
      >
        {m.mapReady && (
          <div className="map-world" style={m.mapTransform}>
            <img
              className="map-terrain"
              src={m.mapImageUrl!}
              alt="Colony surface map"
              draggable={false}
              onLoad={() => m.maybeFitMap(stage())}
            />

            {/* The town hall, i.e. what MineColonies calls the colony centre. */}
            {m.map && (
              <span
                className="map-centre"
                style={m.centreMarkerStyle()}
                title={`Colony centre (${m.map.centerX}, ${m.map.centerZ})`}
              />
            )}

            {/* These markers carry no text, so `title` is their only label — and a title alone is
                not reliably announced by screen readers. aria-label gives them a real accessible
                name, and type="button" keeps them from defaulting to submit behaviour. */}
            {m.mapCitizens.map(c => (
              <button
                key={`c${c.id}`}
                type="button"
                className={`map-dot ${m.citizenDotClass(c)}`}
                style={m.citizenMarkerStyle(c)}
                title={m.citizenTitle(c)}
                aria-label={m.citizenTitle(c)}
                onClick={e => { e.stopPropagation(); if (m.clickedWithoutDrag()) openCitizen(c) }}
              />
            ))}

            {m.mapBuildings.map(b => (
              <button
                key={`b${b.id}`}
                type="button"
                className={`map-pin${b.beingBuilt ? ' building' : ''}`}
                style={m.buildingMarkerStyle(b)}
                title={m.buildingTitle(b)}
                aria-label={m.buildingTitle(b)}
                onClick={e => { e.stopPropagation(); if (m.clickedWithoutDrag()) openBuilding(b) }}
              >
                {/* Decorative: the button's aria-label already names it, so an alt here would
                    only be redundant to a screen reader. */}
                <img
                  src={buildingArt(b) || buildingIcon(b)}
                  alt=""
                  draggable={false}
                  onError={e => buildingIconFallback(e.currentTarget, b)}
                />
                {m.showLabels && <span className="map-pin-label">{b.name}</span>}
              </button>
            ))}
          </div>
        )}

        {/* Everything below sits above the world layer and does not move with it. */}
        {m.map?.available && !m.mapReady && (
          <div className="map-note">
            <p className="empty-title">Drawing the colony map…</p>
            <p className="mt-1">
              The server maps a few chunks at a time, closest to the town hall first.
            </p>
          </div>
        )}

        {m.map && !m.map.available && (
          <div className="map-note">
            <p className="empty-title">No map for this colony.</p>
            <p className="mt-1">{m.map.unavailableReason}</p>
          </div>
        )}

        {m.mapReady && <MapLegend />}
      </div>

      <p className="mt-3 text-[12.5px] text-slate-400">
        Drag to pan, scroll to zoom. The map is drawn from chunks the server has loaded, so
        areas nobody has visited stay blank until they are.
      </p>
    </div>
  )
}
