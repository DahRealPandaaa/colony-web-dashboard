import { pct } from '../../format/format'
import { citizenArt, citizenIconFallback } from '../../hooks/useIcons'
import { Meter } from '../Meter/Meter'
import type { CitizenInfo, Skill } from '../../types/citizen'

interface Props {
  citizen: CitizenInfo
  skills: Skill[]
  onOpen: () => void
}

export default function CitizenCard({ citizen: c, skills, onOpen }: Props) {
  return (
    <article className="card card-click" tabIndex={0}
      onClick={onOpen}
      onKeyDown={e => { if (e.key === 'Enter') onOpen() }}
    >
      <div className="flex items-start gap-3">
        {/* object-top crops the full-body render to a head-and-shoulders bust. */}
        <img
          className="w-12 h-14 object-cover object-top rounded-lg bg-black/30 border border-line shrink-0"
          loading="lazy" src={citizenArt(c)} alt={c.job}
          onError={e => citizenIconFallback(e.currentTarget, c)}
        />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-bold text-[16px] truncate">{c.name}</span>
            {c.child && <span className="ba b-repair">Child</span>}
            <span
              className={`w-1.5 h-1.5 rounded-full shrink-0 ml-auto ${c.spawned ? 'bg-emerald-400' : 'bg-ink-700'}`}
              title={c.spawned ? 'Loaded in the world' : 'Not loaded'}
            />
          </div>
          <div className="text-[12.5px] text-accent-soft font-semibold truncate">{c.job}</div>
          <div className="text-[12.5px] text-slate-400 truncate">{c.workBuilding || 'No workplace'}</div>
        </div>
      </div>

      <div className="mt-3 space-y-2">
        <div>
          <div className="flex justify-between text-[12px] text-slate-400 mb-1">
            <span>Health</span>
            <span className="tabular-nums">{c.health.toFixed(0)} / {c.maxHealth.toFixed(0)}</span>
          </div>
          <Meter variant="hp" pct={pct(c.health, c.maxHealth)} />
        </div>
        <div>
          <div className="flex justify-between text-[12px] text-slate-400 mb-1">
            <span>Saturation</span>
            <span className="tabular-nums">{c.saturation.toFixed(1)}</span>
          </div>
          <Meter variant="food" pct={pct(c.saturation, 20)} />
        </div>
        <div>
          <div className="flex justify-between text-[12px] text-slate-400 mb-1">
            <span>Happiness</span>
            <span className="tabular-nums">{c.happiness.toFixed(1)}</span>
          </div>
          <Meter variant="happy" pct={pct(c.happiness, 10)} />
        </div>
      </div>

      <div className="flex items-center gap-1.5 mt-3 flex-wrap">
        {skills.map(s => (
          <span key={s.name} className={`chip py-0.5! ${s.role ? 'on' : ''}`}>
            <span>{s.name}</span><b className="tabular-nums">{s.level}</b>
          </span>
        ))}
        <span className="ml-auto text-[12px] text-slate-400 tabular-nums">
          {c.inventoryUsed}/{c.inventorySize}
        </span>
      </div>
    </article>
  )
}
