import { Meter } from '../Meter/Meter'
import EquipmentStrip from '../EquipmentStrip/EquipmentStrip'
import House from '../icons/House'
import Shield from '../icons/Shield'
import type { Guard } from '../../types/combat'

interface Props {
  guard: Guard
  healthPct: number
}

export default function GuardCard({ guard: g, healthPct }: Props) {
  const equipment = g.equipment || []

  return (
    <div className="card p-2.5!">
      <div className="flex items-center gap-2.5">
        <span className="w-7 h-7 shrink-0 grid place-items-center rounded-md text-[10px] font-bold
                         bg-rose/15 border border-rose/30 text-rose tabular-nums">
          {g.level}
        </span>
        <div className="flex-1 min-w-0">
          <div className="font-semibold text-[13px] truncate">{g.name}</div>
          <div className="text-[11px] text-text-secondary truncate">{g.job}</div>
        </div>
        <div className="w-20 shrink-0">
          <Meter variant="hp" pct={healthPct} />
          <div className="text-[10px] text-text-secondary tabular-nums mt-0.5 text-right">
            {g.health.toFixed(0)}/{g.maxHealth.toFixed(0)}
          </div>
        </div>
      </div>

      <div className="flex items-center gap-1.5 mt-2 flex-wrap">
        {g.building && (
          <span className="chip py-0.5! text-[11px]">
            <House size={11} />
            <span>{g.building}</span>
            {g.buildingLevel > 0 && <b className="tabular-nums text-[10px]">L{g.buildingLevel}</b>}
          </span>
        )}
        {g.armorPoints > 0 && (
          <span className="chip py-0.5! text-[11px]">
            <Shield size={11} strokeWidth={2} />
            <b className="tabular-nums text-[10px]">{g.armorPoints}</b>
          </span>
        )}
        <span className="ml-auto flex items-center gap-1">
          <EquipmentStrip equipment={equipment} />
          {!equipment.length && <span className="text-[10px] text-text-muted italic">no kit</span>}
        </span>
      </div>
    </div>
  )
}
