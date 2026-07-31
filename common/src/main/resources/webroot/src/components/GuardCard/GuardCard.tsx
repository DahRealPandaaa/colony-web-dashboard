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
    <div className="card p-3!">
      <div className="flex items-center gap-3">
        <span className="w-8 h-8 shrink-0 grid place-items-center rounded-lg text-xs font-bold
                         bg-rose-500/15 border border-rose-500/30 text-rose-300 tabular-nums">
          {g.level}
        </span>
        <div className="flex-1 min-w-0">
          <div className="font-semibold text-sm truncate">{g.name}</div>
          <div className="text-xs text-slate-400 truncate">{g.job}</div>
        </div>
        <div className="w-24 shrink-0">
          <Meter variant="hp" pct={healthPct} />
          <div className="text-xs text-slate-400 tabular-nums mt-1 text-right">
            {g.health.toFixed(0)}/{g.maxHealth.toFixed(0)}
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 mt-2.5 flex-wrap">
        {/* Posted at, with the tower's level: a level 5 tower is where the best kit lives. */}
        {g.building && (
          <span className="chip py-1!">
            <House size={12} />
            <span>{g.building}</span>
            {g.buildingLevel > 0 && <b className="tabular-nums">L{g.buildingLevel}</b>}
          </span>
        )}
        {g.armorPoints > 0 && (
          <span className="chip py-1!">
            <Shield size={12} strokeWidth={2} />
            <b className="tabular-nums">{g.armorPoints}</b>
          </span>
        )}

        <span className="ml-auto flex items-center gap-1">
          <EquipmentStrip equipment={equipment} />
          {!equipment.length && <span className="text-xs text-slate-500 italic">no kit</span>}
        </span>
      </div>
    </div>
  )
}
