import { pct, stateClass, stateLabel } from '../../format/format'
import { Progress } from '../Meter/Meter'
import ResearchCostItem from '../ResearchCostItem/ResearchCostItem'
import type { ResearchEntry } from '../../types/research'

/** Card border tracks the state: running is highlighted, done is green, locked is dimmed. */
function cardClass(state: string): string {
  if (state === 'IN_PROGRESS') return 'card-active'
  if (state === 'COMPLETED') return 'border-emerald-500/25'
  return 'opacity-75'
}

/**
 * Research card — always shows cost items and effects.
 * The old design hid costs when not in progress, leaving "death space" for not-started entries.
 */
export default function ResearchCard({ entry: r }: { entry: ResearchEntry }) {
  const hasCosts = (r.cost || []).length > 0
  const hasEffects = (r.effects || []).length > 0
  const isInProgress = r.state === 'IN_PROGRESS'

  return (
    <div className={`card-compact ${cardClass(r.state)}`}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <span className="font-semibold text-sm">{r.name}</span>
          <div className="text-xs text-text-secondary mt-0.5">Tier {r.depth}</div>
        </div>
        <span className={`state shrink-0 ${stateClass(r.state)}`}>{stateLabel(r.state)}</span>
      </div>

      {isInProgress && r.maxProgress > 0 && (
        <div className="mt-2">
          <Progress pct={pct(r.progress, r.maxProgress)} />
          <div className="text-2xs text-accent-soft font-bold tabular-nums mt-1">
            {pct(r.progress, r.maxProgress)}%
          </div>
        </div>
      )}

      {/* Always show effects and costs — no empty cards */}
      {hasEffects && (
        <ul className="mt-2 space-y-0.5">
          {r.effects.map((e, i) => (
            <li key={i} className="text-xs text-success truncate">+ {e}</li>
          ))}
        </ul>
      )}

      {hasCosts && (
        <div className="flex items-center gap-2 mt-2 flex-wrap">
          {r.cost.map((c, i) => <ResearchCostItem key={i} item={c} />)}
        </div>
      )}
    </div>
  )
}
