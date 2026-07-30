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
        <div className="font-semibold text-[14.5px]">{builder.name}</div>
        <div className="text-slate-400 text-[12px] mt-0.5">Idle</div>
      </div>
    )
  }

  return (
    <div className="card p-3!">
      <div className="flex items-center gap-2">
        <span className={`ba ${badgeClass(task.action)}`}>{task.action}</span>
        <span className="ml-auto text-[12.5px] text-slate-400 truncate">{builder.name}</span>
      </div>
      <div className="mt-1.5 flex items-baseline gap-2">
        <span className="font-semibold text-[14.5px] truncate">{task.building}</span>
        <span className="text-slate-400 text-[12.5px] font-semibold tabular-nums shrink-0">
          {task.current} → {task.target}
        </span>
      </div>
      <Progress pct={task.pct} />
      <div className="text-accent-soft text-[11.5px] font-bold tabular-nums mt-1.5">{task.pct}%</div>
    </div>
  )
}
