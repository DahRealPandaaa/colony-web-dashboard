import { useColony } from '../../contexts/ColonyContext'
import { useOverview } from '../../hooks/useOverview'
import { num, pct, statusOf } from '../../format/format'
import type { BuildingInfo } from '../../types/building'
import WorkOrderCard from '../../components/WorkOrderCard/WorkOrderCard'
import BuilderCard from '../../components/BuilderCard/BuilderCard'
import Panel from '../../components/Panel/Panel'
import { Meter, Progress } from '../../components/Meter/Meter'
import House from '../../components/icons/House'

function happinessClass(h: number): string {
  if (h >= 7) return 'text-success'
  return h >= 5 ? 'text-amber' : 'text-rose'
}

export function OverviewTab() {
  const { snap, stats, research, colonies, colonyId, setTab, openBuilding } = useColony()
  const { builderInfo, activeWorkOrders } = useOverview()
  const colony = colonies.find(c => c.id === colonyId)

  // In-progress research items — data now loaded on overview tab
  const activeResearch = research?.branches
    ?.flatMap(b => b.researches)
    .filter(e => e.state === 'IN_PROGRESS') ?? []

  return (
    <div className="animate-fade-up flex flex-col gap-6">
      {/* ── Hero banner ── */}
      <div className="hero-banner flex items-center gap-4 flex-wrap">
        <span className="brand-mark w-12 h-12 shrink-0">
          <House size={22} />
        </span>
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-bold font-display tracking-tight">
            {colony?.name ?? 'Colony'}
          </h1>
          <p className="text-sm text-text-secondary mt-0.5">
            {stats.citizens} citizens · {stats.buildings} buildings · {stats.guards} guards
          </p>
        </div>
        <div className="flex items-center gap-4 flex-wrap">
          <div className="text-right">
            <div className="text-xs text-text-secondary uppercase tracking-wider font-semibold">Happiness</div>
            <div className={`text-2xl font-bold font-display tabular-nums ${happinessClass(stats.happiness)}`}>
              {stats.happiness.toFixed(1)}
            </div>
          </div>
          <div className="text-right">
            <div className="text-xs text-text-secondary uppercase tracking-wider font-semibold">Nights safe</div>
            <div className={`text-2xl font-bold font-display tabular-nums ${stats.raided ? 'text-rose' : ''}`}>
              {stats.raided ? 'RAID!' : stats.nightsSinceRaid}
            </div>
          </div>
        </div>
      </div>

      {/* ── Quick stats bar ── */}
      <div className="flex items-center gap-4 flex-wrap text-sm bg-ink-900/60 border border-line rounded-xl px-5 py-3">
        <span className="text-text-secondary">
          <span className="text-text-primary font-semibold tabular-nums">{stats.citizens} / {stats.maxCitizens || '—'}</span> citizens
        </span>
        <span className="text-text-muted">·</span>
        <span className="text-text-secondary">
          <span className="text-text-primary font-semibold tabular-nums">{stats.buildings}</span> buildings
          {' · '}
          <span className="text-text-primary font-semibold tabular-nums">{stats.decorations}</span> decorations
        </span>
        <span className="text-text-muted">·</span>
        <span className="text-text-secondary">
          <span className="text-text-primary font-semibold tabular-nums">{num(stats.warehouseItems)}</span> in warehouse
        </span>
        <span className="text-text-muted">·</span>
        <span className="text-text-secondary">
          <span className="text-text-primary font-semibold tabular-nums">{stats.researchCompleted}</span> research done
        </span>
        <button
          className="chip chip-btn ml-auto"
          onClick={() => setTab('map')}
        >
          Open map →
        </button>
      </div>

      {/* ── Work orders + side panels ── */}
      <div className="grid grid-cols-1 xl:grid-cols-[1fr_600px] gap-4">
        {/* Work orders */}
        <Panel title="Work orders" subtitle="Claimed orders first" count={snap.workOrders.length}>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
            {activeWorkOrders.map(wo => {
              const bld = snap.buildings.find(b => b.workOrderId === wo.id)
              const counts = bld ? {
                ok: (bld.required || []).filter(r => statusOf(r) === 'ok').length,
                deliver: (bld.required || []).filter(r => statusOf(r) === 'deliver').length,
                missing: (bld.required || []).filter(r => statusOf(r) === 'missing').length,
              } : undefined
              return <WorkOrderCard key={wo.id} order={wo} onOpen={bld ? () => openBuilding(bld) : undefined} counts={counts} />
            })}
            {!snap.workOrders.length && <p className="empty col-span-full">Nothing is being built right now.</p>}
          </div>
        </Panel>

        {/* Side: Research + Builders */}
        <div className="flex flex-col gap-4">
          {activeResearch.length > 0 ? (
            <Panel title="Researching now" subtitle={`${stats.researchInProgress} underway`}>
              <div className="space-y-2">
                {activeResearch.map(e => (
                  <div key={e.id} className="card p-3!">
                    <div className="font-semibold text-sm">{e.name}</div>
                    <div className="text-xs text-text-secondary mt-0.5">{e.branch} · tier {e.depth}</div>
                    <Progress pct={pct(e.progress, e.maxProgress)} />
                    <div className="text-xs text-violet-300 font-bold tabular-nums mt-1">
                      {pct(e.progress, e.maxProgress)}%
                    </div>
                  </div>
                ))}
              </div>
            </Panel>
          ) : (
            <Panel title="Research" subtitle={`${stats.researchCompleted} completed`}>
              <button
                className="w-full text-sm text-accent-soft hover:text-accent transition-colors text-center py-2"
                onClick={() => setTab('research')}
              >
                Open research tab →
              </button>
            </Panel>
          )}

          <Panel title="Builders" subtitle="Who is on what" count={snap.builders.length}>
            <div className="space-y-2">
              {snap.builders.map(b => <BuilderCard key={b.id} builder={b} task={builderInfo(b)} />)}
              {!snap.builders.length && <p className="empty text-sm">No builders assigned.</p>}
            </div>
          </Panel>
        </div>
      </div>
    </div>
  )
}
