import { badgeClass } from '../../format/format'
import { Progress } from '../Meter/Meter'
import type { WorkOrderInfo } from '../../types/building'

export default function WorkOrderCard({ order }: { order: WorkOrderInfo }) {
  return (
    <div className="card p-3! flex items-center gap-3">
      <span className={`ba shrink-0 ${badgeClass(order.action)}`}>{order.action}</span>
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-[14.5px] truncate">
          {order.buildingName || order.buildingType || 'Structure'}
        </div>
        <Progress pct={Math.round((order.progress || 0) * 100)} />
      </div>
      <div className="text-right shrink-0">
        <div className="text-[12px] text-slate-400 tabular-nums">
          {order.currentLevel} → {order.targetLevel}
        </div>
        <div className={`text-[12px] font-semibold ${order.builderName ? 'text-accent-soft' : 'text-slate-400'}`}>
          {order.builderName || 'unclaimed'}
        </div>
      </div>
    </div>
  )
}
