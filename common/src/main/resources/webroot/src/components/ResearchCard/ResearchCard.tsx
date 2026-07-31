import { pct, stateClass, stateLabel } from '../../format/format'
import { Progress } from '../Meter/Meter'
import ResearchCostItem from '../ResearchCostItem/ResearchCostItem'
import type { ResearchEntry } from '../../types/research'

/** Card border tracks the state: running is highlighted, done is green, locked is dimmed. */
function cardClass(state: string): string {
  if (state === 'IN_PROGRESS') return 'card-active'
  if (state === 'COMPLETED') return 'border-emerald-500/25'
  return 'opacity-80'
}

export default function ResearchCard({ entry: r }: { entry: ResearchEntry }) {
  return (
    <div className={`card p-3! ${cardClass(r.state)}`}>
      <div className="flex items-start justify-between gap-2">
        <span className="font-semibold text-sm">{r.name}</span>
        <span className={`state shrink-0 ${stateClass(r.state)}`}>{stateLabel(r.state)}</span>
      </div>
      <div className="text-xs text-slate-400 mt-0.5">Tier {r.depth}</div>

      {r.state === 'IN_PROGRESS' && r.maxProgress > 0 && (
        <div className="mt-2">
          <Progress pct={pct(r.progress, r.maxProgress)} />
          <div className="text-2xs text-accent-soft font-bold tabular-nums mt-1">
            {pct(r.progress, r.maxProgress)}%
          </div>
        </div>
      )}

      {(r.effects || []).length > 0 && (
        <ul className="mt-2 space-y-0.5">
          {r.effects.map((e, i) => (
            <li key={i} className="text-xs text-emerald-300/90 truncate">+ {e}</li>
          ))}
        </ul>
      )}

      {(r.cost || []).length > 0 && (
        <div className="flex items-center gap-2 mt-2 flex-wrap">
          {r.cost.map((c, i) => <ResearchCostItem key={i} item={c} />)}
        </div>
      )}
    </div>
  )
}
