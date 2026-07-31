import { useCombat } from '../../hooks/useCombat'
import GuardCard from '../../components/GuardCard/GuardCard'
import GuardPostCard from '../../components/GuardPostCard/GuardPostCard'
import Panel from '../../components/Panel/Panel'
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
      <div className={`panel rounded-xl! px-4 py-3.5 flex items-center gap-3.5 ${
        combat.underAttack ? 'border-rose-500/50! bg-rose-500/[0.08]!' : ''}`}>
        <span className={`w-10 h-10 shrink-0 grid place-items-center rounded-xl border ${
          combat.underAttack
            ? 'bg-rose/15 border-rose/40 text-rose'
            : 'bg-emerald/10 border-emerald/30 text-emerald'}`}>
          <Shield />
        </span>
        <div className="min-w-0">
          <div className={`font-bold text-base font-display ${combat.underAttack ? 'text-rose' : ''}`}>
            {c.raidHeadline}
          </div>
          <div className="text-sm text-text-secondary">{raidLine}</div>
        </div>
      </div>

      {/* Stat row — compact */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5">
        <div className="tile p-2.5!">
          <div className="tile-label">Guards</div>
          <div className="text-lg font-bold font-display tabular-nums mt-1">{combat.guardCount} / {combat.guardCapacity}</div>
          <div className="tile-sub mt-1!">Avg Lv {combat.averageGuardLevel.toFixed(1)} · HP {Math.round(combat.averageHealthPct)}%</div>
        </div>
        <div className="tile p-2.5!">
          <div className="tile-label">Guard posts</div>
          <div className="text-lg font-bold font-display tabular-nums mt-1">{combat.posts.length}</div>
          <div className="tile-sub mt-1!">{combat.posts.filter(p => p.assigned > 0).length} staffed</div>
        </div>
        <div className="tile p-2.5!">
          <div className="tile-label">Avg. health</div>
          <div className="text-lg font-bold font-display tabular-nums mt-1">{Math.round(combat.averageHealthPct)}%</div>
          <Meter variant="hp" pct={Math.round(combat.averageHealthPct)} className="mt-1.5" />
        </div>
        <div className="tile p-2.5!">
          <div className="tile-label">Graves</div>
          <div className={`text-lg font-bold font-display tabular-nums mt-1 ${combat.graves ? 'text-rose' : ''}`}>{combat.graves}</div>
          <div className="tile-sub mt-1!">{combat.graves ? 'need burying' : 'All clear'}</div>
        </div>
      </div>

      {/* Guard roster + side panel (posts + events) */}
      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-4">
        <Panel title="Guard roster" subtitle="Every citizen with a combat job" count={combat.guards.length} className="self-start">
          <div className="space-y-2">
            {combat.guards.map(g => (
              <GuardCard key={g.id} guard={g} healthPct={c.guardHealthPct(g)} />
            ))}
            {!combat.guards.length && <p className="empty">No guards employed.</p>}
          </div>
        </Panel>

        <div className="flex flex-col gap-4">
          {/* Guard posts — compact list */}
          <Panel title="Guard posts" subtitle="Towers and barracks" count={combat.posts.length}>
            <div className="space-y-1.5">
              {combat.posts.map(p => (
                <GuardPostCard key={p.id} post={p} status={c.postStatus(p)} />
              ))}
              {!combat.posts.length && <p className="empty text-sm">No guard towers or barracks built.</p>}
            </div>
          </Panel>

          {combat.events.length > 0 && (
            <Panel title="Active events" className="border-rose/30!">
              <div className="space-y-2">
                {combat.events.map(e => (
                  <div key={e.id} className="card-compact border-rose/30! bg-rose-500/[0.05]!">
                    <div className="font-semibold text-sm">{e.name}</div>
                    <div className="text-xs text-text-secondary tabular-nums">
                      {e.status} · {e.x}, {e.y}, {e.z}
                    </div>
                  </div>
                ))}
              </div>
            </Panel>
          )}
        </div>
      </div>
    </div>
  )
}
