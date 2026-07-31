import { useCombat } from '../../hooks/useCombat'
import StatTile from '../../components/StatTile/StatTile'
import GuardCard from '../../components/GuardCard/GuardCard'
import GuardPostCard from '../../components/GuardPostCard/GuardPostCard'
import { Meter } from '../../components/Meter/Meter'
import Shield from '../../components/icons/Shield'

export function CombatTab() {
  const c = useCombat()
  const combat = c.combat

  if (!combat) return <div className="empty">Loading combat data…</div>

  const raidLine = `${combat.nightsSinceRaid} nights since the last raid · raid level ${combat.raidLevel}`
    + (combat.spiesEnabled ? ' · spies active' : '')

  return (
    <div className="animate-fade-up flex flex-col gap-4">
      {/* Raid banner */}
      <div className={`panel rounded-2xl! px-4 py-3.5 flex items-center gap-3.5 ${
        combat.underAttack ? 'border-rose-500/50! bg-rose-500/[0.08]!' : ''}`}>
        <span className={`w-10 h-10 shrink-0 grid place-items-center rounded-xl border ${
          combat.underAttack
            ? 'bg-rose-500/15 border-rose-500/40 text-rose-300'
            : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'}`}>
          <Shield />
        </span>
        <div className="min-w-0">
          <div className={`font-bold text-base ${combat.underAttack ? 'text-rose-300' : ''}`}>
            {c.raidHeadline}
          </div>
          <div className="text-sm text-slate-400">{raidLine}</div>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        <StatTile label="Guards" value={combat.guardCount} sub={`${combat.guardCapacity} slots`} />
        <StatTile label="Avg. level" value={combat.averageGuardLevel.toFixed(1)} />
        <StatTile label="Avg. health" value={`${Math.round(combat.averageHealthPct)}%`}>
          <Meter variant="hp" pct={Math.round(combat.averageHealthPct)} className="mt-2" />
        </StatTile>
        <StatTile label="Guard posts" value={combat.posts.length} />
        <StatTile
          label="Graves"
          value={combat.graves}
          valueClass={combat.graves ? 'text-rose-300' : ''}
          sub={combat.graves ? 'need burying' : undefined}
        />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-4">
        <section className="panel self-start">
          <div className="panel-head">
            <div>
              <h2 className="panel-title">Guard roster</h2>
              <p className="panel-sub">Every citizen with a combat job</p>
            </div>
            <span className="chip">{combat.guards.length}</span>
          </div>
          <div className="panel-body space-y-2">
            {combat.guards.map(g => (
              <GuardCard key={g.id} guard={g} healthPct={c.guardHealthPct(g)} />
            ))}
            {!combat.guards.length && <p className="empty">No guards employed.</p>}
          </div>
        </section>

        <div className="flex flex-col gap-4">
          <aside className="panel">
            <div className="panel-head">
              <div>
                <h2 className="panel-title">Guard posts</h2>
                <p className="panel-sub">Towers and barracks</p>
              </div>
            </div>
            <div className="panel-body space-y-2">
              {combat.posts.map(p => (
                <GuardPostCard key={p.id} post={p} status={c.postStatus(p)} />
              ))}
              {!combat.posts.length && <p className="empty">No guard towers or barracks built.</p>}
            </div>
          </aside>

          {combat.events.length > 0 && (
            <aside className="panel border-rose-500/30!">
              <div className="panel-head">
                <h2 className="panel-title text-rose-300">Active events</h2>
              </div>
              <div className="panel-body space-y-2">
                {combat.events.map(e => (
                  <div key={e.id} className="card p-3! border-rose-500/30! bg-rose-500/[0.05]!">
                    <div className="font-semibold text-sm">{e.name}</div>
                    <div className="text-xs text-slate-400 tabular-nums">
                      {e.status} · {e.x}, {e.y}, {e.z}
                    </div>
                  </div>
                ))}
              </div>
            </aside>
          )}
        </div>
      </div>
    </div>
  )
}
