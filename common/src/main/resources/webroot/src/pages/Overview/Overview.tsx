import { useColony } from '../../contexts/ColonyContext'
import { useOverview } from '../../hooks/useOverview'
import { num, pct } from '../../format/format'
import StatTile from '../../components/StatTile/StatTile'
import WorkOrderCard from '../../components/WorkOrderCard/WorkOrderCard'
import BuilderCard from '../../components/BuilderCard/BuilderCard'
import { Meter } from '../../components/Meter/Meter'

/** Happiness reads green above 7, amber above 5, red below. */
function happinessClass(happiness: number): string {
  if (happiness >= 7) return 'text-emerald-300'
  return happiness >= 5 ? 'text-amber-300' : 'text-rose-300'
}

export function OverviewTab() {
  const { snap, stats } = useColony()
  const { builderInfo, activeWorkOrders } = useOverview()

  return (
    <div className="grid grid-cols-1 xl:grid-cols-[1fr_340px] gap-4 animate-fade-up">
      <div className="min-w-0 flex flex-col gap-4">
        {/* Headline numbers */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <StatTile
            label="Citizens"
            value={`${stats.citizens} / ${stats.maxCitizens || '—'}`}
            sub={`${stats.children} children · ${stats.unemployed} idle`}
          />
          <StatTile
            label="Happiness"
            value={stats.happiness.toFixed(1)}
            valueClass={happinessClass(stats.happiness)}
          >
            <Meter variant="happy" pct={pct(stats.happiness, 10)} className="mt-2" />
          </StatTile>
          <StatTile
            label="Buildings"
            value={stats.buildings}
            sub={`${stats.decorations} decorations · ${stats.workOrders} work orders`}
          />
          <StatTile
            label="Defence"
            value={stats.guards}
            valueClass={stats.raided ? 'text-rose-300' : ''}
            sub={stats.raided ? 'Under attack!' : `${stats.nightsSinceRaid} nights since raid`}
            subClass={stats.raided ? 'text-rose-300 font-semibold' : ''}
          />
          <StatTile
            label="Warehouse"
            value={num(stats.warehouseItems)}
            sub={`${stats.warehouseTypes} distinct items`}
          />
          <StatTile
            label="Research"
            value={stats.researchCompleted}
            sub={`${stats.researchInProgress} in progress`}
          />
          <StatTile
            label="Builders"
            value={stats.builders}
            sub={`${snap.workOrders.length} orders queued`}
          />
          <StatTile label="Avg. saturation" value={stats.saturation.toFixed(1)}>
            <Meter variant="food" pct={pct(stats.saturation, 20)} className="mt-2" />
          </StatTile>
        </div>

        {/* Work orders */}
        <section className="panel">
          <div className="panel-head">
            <div>
              <h2 className="panel-title">Work orders</h2>
              <p className="panel-sub">Claimed orders first</p>
            </div>
            <span className="chip">{snap.workOrders.length} total</span>
          </div>
          <div className="panel-body space-y-2">
            {activeWorkOrders.map(wo => <WorkOrderCard key={wo.id} order={wo} />)}
            {!snap.workOrders.length && <p className="empty">Nothing is being built right now.</p>}
          </div>
        </section>
      </div>

      {/* Builders */}
      <aside className="panel self-start">
        <div className="panel-head">
          <div>
            <h2 className="panel-title">Builders</h2>
            <p className="panel-sub">Who is on what</p>
          </div>
          <span className="chip">{snap.builders.length}</span>
        </div>
        <div className="panel-body space-y-2.5">
          {snap.builders.map(b => (
            <BuilderCard key={b.id} builder={b} task={builderInfo(b)} />
          ))}
          {!snap.builders.length && <p className="empty">No builders assigned.</p>}
        </div>
      </aside>
    </div>
  )
}
