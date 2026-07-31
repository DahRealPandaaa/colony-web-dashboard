import { useState } from 'react'
import { badgeClass } from '../../format/format'
import { buildingArt, buildingIcon, buildingIconFallback } from '../../hooks/useIcons'
import { Progress } from '../Meter/Meter'
import type { WorkOrderInfo } from '../../types/building'

interface Props {
  order: WorkOrderInfo
  onOpen?: () => void
  counts?: { ok: number; deliver: number; missing: number }
}

/** Card with large icon, corner badge, progress + resource dots. */
export default function WorkOrderCard({ order, onOpen, counts }: Props) {
  const pct = Math.round((order.progress || 0) * 100)
  const building = { type: order.buildingType, kind: (order.buildingType === 'decoration' ? 'decoration' : 'building') } as const
  const [imgRef, setImgRef] = useState<HTMLImageElement | null>(null)

  return (
    <article
      className={`card p-3! flex flex-col items-center text-center gap-2 relative${onOpen ? ' card-click' : ''}`}
      tabIndex={onOpen ? 0 : undefined}
      onClick={onOpen}
      onKeyDown={onOpen ? e => { if (e.key === 'Enter') onOpen() } : undefined}
    >
      {/* Badge in top-right corner */}
      <span className={`ba shrink-0 absolute top-2.5 right-2.5 ${badgeClass(order.action)}`}>
        {order.action}
      </span>

      {/* Large icon */}
      <img
        className="w-14 h-14 object-contain pixelated rounded-xl bg-black/20 border border-line p-0.5 shrink-0 mt-1"
        loading="lazy"
        src={buildingArt(building) || buildingIcon(building)}
        alt={order.buildingName || 'Building'}
        ref={setImgRef}
        onError={() => { if (imgRef) buildingIconFallback(imgRef, building) }}
      />

      {/* Name + level */}
      <div className="w-full">
        <span className="font-semibold text-sm truncate block">
          {order.buildingName || order.buildingType || 'Structure'}
        </span>
        <div className="text-xs text-text-secondary tabular-nums mt-0.5">
          Lv {order.currentLevel} → {order.targetLevel}
        </div>
        {order.builderName && (
          <div className="text-xs text-accent-soft font-semibold truncate mt-0.5">
            {order.builderName}
          </div>
        )}
      </div>

      {/* Progress */}
      <div className="w-full flex items-center gap-2">
        <div className="flex-1"><Progress pct={pct} /></div>
        <b className="text-xs text-accent-soft tabular-nums shrink-0">{pct}%</b>
      </div>

      {/* Resource dots */}
      {counts && (counts.ok > 0 || counts.deliver > 0 || counts.missing > 0) && (
        <div className="flex items-center gap-2 flex-wrap justify-center">
          {counts.ok > 0 && <span className="sdot ok"><i /><b>{counts.ok}</b></span>}
          {counts.deliver > 0 && <span className="sdot deliver"><i /><b>{counts.deliver}</b></span>}
          {counts.missing > 0 && <span className="sdot missing"><i /><b>{counts.missing}</b></span>}
        </div>
      )}
    </article>
  )
}
