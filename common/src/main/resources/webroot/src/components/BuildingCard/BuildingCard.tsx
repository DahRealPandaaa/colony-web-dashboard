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

  return (
    <article
      className={`card card-click relative ${building.beingBuilt ? 'card-active' : ''}`}
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={e => { if (e.key === 'Enter') onOpen() }}
    >
      {building.kind === 'decoration' && <span className="ba b-repair absolute top-3 right-3">Deco</span>}

      <div className="flex items-center gap-3">
        <img
          className="w-14 h-14 object-contain pixelated rounded-lg bg-black/25 border border-line p-0.5 shrink-0"
          loading="lazy"
          src={buildingArt(building) || buildingIcon(building)}
          alt={building.name}
          onError={e => buildingIconFallback(e.currentTarget, building)}
        />
        <div className="flex-1 min-w-0">
          <span className="block font-bold text-base truncate">{building.name}</span>
          <span className="text-slate-400 text-sm">
            Level{' '}
            <b className="text-slate-200 tabular-nums">
              {targetLevel ? `${building.level} → ${targetLevel}` : building.level}
            </b>
          </span>
        </div>
      </div>

      {building.beingBuilt && (
        <div className="mt-3">
          <div className="flex items-center justify-between text-xs">
            <span className="text-slate-400 truncate">
              {builtBy ? <>Built by <b className="text-accent-soft">{builtBy}</b></> : 'Queued, no builder yet'}
            </span>
            <b className="text-accent-soft tabular-nums">{progress}%</b>
          </div>
          <Progress pct={progress} />
        </div>
      )}

      {required.length > 0 && (
        <div className="flex items-center gap-2.5 mt-3 flex-wrap">
          {counts.ok > 0 && <span className="sdot ok"><i /><b>{counts.ok}</b></span>}
          {counts.deliver > 0 && <span className="sdot deliver"><i /><b>{counts.deliver}</b></span>}
          {counts.missing > 0 && <span className="sdot missing"><i /><b>{counts.missing}</b></span>}
          <span className="ml-auto text-xs text-slate-400">{required.length} items</span>
        </div>
      )}
    </article>
  )
}
