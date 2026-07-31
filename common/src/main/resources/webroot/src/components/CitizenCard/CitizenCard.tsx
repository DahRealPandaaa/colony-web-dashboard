import { citizenArt, citizenIconFallback } from '../../hooks/useIcons'
import type { CitizenInfo, Skill } from '../../types/citizen'

function moodColor(value: number, max: number, kind: 'hp' | 'food' | 'happy'): string {
  const ratio = value / max
  if (kind === 'hp' || kind === 'food') {
    if (ratio >= 0.7) return 'bg-emerald-400'
    return ratio >= 0.35 ? 'bg-amber-400' : 'bg-rose-500'
  }
  // happiness: 0-10 scale
  if (value >= 7) return 'bg-emerald-400'
  return value >= 5 ? 'bg-amber-400' : 'bg-rose-500'
}

interface Props {
  citizen: CitizenInfo
  skills: Skill[]
  onOpen: () => void
}

export default function CitizenCard({ citizen: c, skills, onOpen }: Props) {
  return (
    <article className="card card-click flex flex-col gap-3" tabIndex={0}
      onClick={onOpen}
      onKeyDown={e => { if (e.key === 'Enter') onOpen() }}
    >
      <div className="flex items-start gap-3">
        <img
          className="w-12 h-14 object-cover object-top rounded-lg bg-black/30 border border-line shrink-0"
          loading="lazy" src={citizenArt(c)} alt={c.job}
          onError={e => citizenIconFallback(e.currentTarget, c)}
        />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-bold text-base truncate">{c.name}</span>
            {c.child && <span className="ba b-repair">Child</span>}
            <span
              className={`w-1.5 h-1.5 rounded-full shrink-0 ml-auto ${c.spawned ? 'bg-emerald-400' : 'bg-ink-700'}`}
              title={c.spawned ? 'Loaded in the world' : 'Not loaded'}
            />
          </div>
          <div className="text-xs text-accent-soft font-semibold truncate">{c.job}</div>
          <div className="text-xs text-text-secondary truncate">{c.workBuilding || 'No workplace'}</div>
        </div>
      </div>

      {/* Compact health / saturation / happiness indicators with labels */}
      <div className="flex items-center gap-3 text-xs">
        <span className="flex items-center gap-1.5" title={`HP: ${c.health.toFixed(0)} / ${c.maxHealth.toFixed(0)}`}>
          <span className={`w-2 h-2 rounded-full ${moodColor(c.health, c.maxHealth, 'hp')}`} />
          <span className="text-text-secondary">HP</span>
          <span className="text-text-primary tabular-nums font-semibold">{c.health.toFixed(0)}</span>
        </span>
        <span className="flex items-center gap-1.5" title={`Saturation: ${c.saturation.toFixed(1)} / 20`}>
          <span className={`w-2 h-2 rounded-full ${moodColor(c.saturation, 20, 'food')}`} />
          <span className="text-text-secondary">Food</span>
          <span className="text-text-primary tabular-nums font-semibold">{c.saturation.toFixed(1)}</span>
        </span>
        <span className="flex items-center gap-1.5" title={`Happiness: ${c.happiness.toFixed(1)} / 10`}>
          <span className={`w-2 h-2 rounded-full ${moodColor(c.happiness, 10, 'happy')}`} />
          <span className="text-text-secondary">Mood</span>
          <span className="text-text-primary tabular-nums font-semibold">{c.happiness.toFixed(1)}</span>
        </span>
      </div>

      {/* Skill chips */}
      <div className="flex items-center gap-1.5 flex-wrap">
        {skills.map(s => (
          <span key={s.name} className={`chip py-0.5! text-xs on `}>
            <span>{s.name}</span><b className="tabular-nums">{s.level}</b>
          </span>
        ))}
        <span className="ml-auto text-xs text-text-secondary tabular-nums">
          {c.inventoryUsed}/{c.inventorySize}
        </span>
      </div>
    </article>
  )
}
