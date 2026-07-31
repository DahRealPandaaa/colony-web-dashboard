import { badgeClass } from '../../format/format'
import { Progress } from '../Meter/Meter'
import type { BuilderInfo } from '../../types/building'
import type { BuilderTask } from '../../hooks/useOverview'

interface Props {
  builder: BuilderInfo
  task: BuilderTask
}

export default function BuilderCard({ builder, task }: Props) {
  if (task.idle) {
    return (
      <div className="card p-3!">
        <div className="font-semibold text-sm">{builder.name}</div>
        <div className="text-text-secondary text-xs mt-0.5">Idle</div>
      </div>
    )
  }

  return (
    <div className="card p-3!">
      <div className="flex items-center gap-2">
        <span className={`ba ${badgeClass(task.action)}`}>{task.action}</span>
        <span className="ml-auto text-xs text-text-secondary truncate">{builder.name}</span>
      </div>
      <div className="mt-1.5 flex items-baseline gap-2">
        <span className="font-semibold text-sm truncate">{task.building}</span>
        <span className="text-text-secondary text-xs font-semibold tabular-nums shrink-0">
          {task.current} → {task.target}
        </span>
      </div>
      <Progress pct={task.pct} />
      <div className="text-accent-soft text-xs font-bold tabular-nums mt-1.5">{task.pct}%</div>
    </div>
  )
}
