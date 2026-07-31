import { buildingArt, buildingIcon, buildingIconFallback } from '../../hooks/useIcons'
import { Progress } from '../Meter/Meter'
import type { BuildingInfo } from '../../types/building'

interface Props {
  building: BuildingInfo
  targetLevel: number
  progress: number
  builtBy: string | null
  counts: { ok: number; deliver: number; missing: number }
  onOpen: () => void
}

export default function BuildingCard({ building, targetLevel, progress, builtBy, counts, onOpen }: Props) {
  const required = building.required || []

  const content = (
    <>
      <div className="flex items-center gap-3">
        <img
          className="w-12 h-12 object-contain pixelated rounded-lg bg-black/25 border border-line p-0.5 shrink-0"
          loading="lazy"
          src={buildingArt(building) || buildingIcon(building)}
          alt={building.name}
          onError={e => buildingIconFallback(e.currentTarget, building)}
        />
        <div className="flex-1 min-w-0">
          <span className="block font-semibold text-sm truncate">{building.name}</span>
          <span className="text-xs text-text-secondary tabular-nums">
            Level {targetLevel && building.beingBuilt
              ? `${building.level} → ${targetLevel}`
              : building.level}
          </span>
          {building.kind === 'decoration' && (
            <span className="ba b-repair ml-1.5 align-middle">Deco</span>
          )}
        </div>
        {!building.beingBuilt && required.length > 0 && (
          <span className="text-xs text-text-secondary tabular-nums shrink-0">{required.length} items</span>
        )}
      </div>

      {building.beingBuilt && (
        <>
          <div className="mt-3">
            <div className="flex items-center justify-between text-xs">
              <span className="text-text-secondary truncate">
                {builtBy ? <>Built by <b className="text-accent-soft">{builtBy}</b></> : 'Queued, no builder yet'}
              </span>
              <b className="text-accent-soft tabular-nums">{progress}%</b>
            </div>
            <Progress pct={progress} />
          </div>

          {required.length > 0 && (
            <div className="flex items-center gap-2.5 mt-3 flex-wrap">
              {counts.ok > 0 && <span className="sdot ok"><i /><b>{counts.ok}</b></span>}
              {counts.deliver > 0 && <span className="sdot deliver"><i /><b>{counts.deliver}</b></span>}
              {counts.missing > 0 && <span className="sdot missing"><i /><b>{counts.missing}</b></span>}
              <span className="ml-auto text-xs text-text-secondary">{required.length} items</span>
            </div>
          )}
        </>
      )}
    </>
  )

  // Both idle and being-built use the same card style — idle just has less content
  if (!building.beingBuilt) {
    return (
      <article className="card card-click flex flex-col" tabIndex={0}
        onClick={onOpen}
        onKeyDown={e => { if (e.key === 'Enter') onOpen() }}
      >
        {content}
      </article>
    )
  }

  return (
    <article className="card card-click card-active flex flex-col" tabIndex={0}
      onClick={onOpen}
      onKeyDown={e => { if (e.key === 'Enter') onOpen() }}
    >
      {building.kind === 'decoration' && <span className="ba b-repair self-end -mb-1">Deco</span>}
      {content}
    </article>
  )
}
