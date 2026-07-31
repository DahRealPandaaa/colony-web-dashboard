import { useNeeded } from '../../hooks/useNeeded'
import { num, stacks } from '../../format/format'
import type { NeededSort } from '../../contexts/UiContext'
import ItemTooltip from '../../components/ItemTooltip/ItemTooltip'
import Panel from '../../components/Panel/Panel'
import SearchInput from '../../components/SearchInput/SearchInput'

export function NeededTab() {
  const n = useNeeded()

  return (
    <div className="animate-fade-up flex flex-col gap-4">
      <div className="toolbar">
        <SearchInput className="flex-1 min-w-[180px]" value={n.neededSearch} onChange={n.setNeededSearch}
          placeholder="Search items or materials…" />
        <select className="field py-1.5" aria-label="Sort missing items"
          value={n.neededSort} onChange={e => n.setNeededSort(e.target.value as NeededSort)}>
          <option value="shortfall">Biggest gap</option>
          <option value="alpha">Name</option>
        </select>
        <span className={`chip ${n.missingCount ? 'bad' : 'good'}`}>
          {num(n.neededTotal)} items to find
        </span>
        {n.waitingSites > 0 && (
          <span className="chip">{n.waitingSites === 1 ? '1 site waiting' : `${n.waitingSites} sites waiting`}</span>
        )}
      </div>

      {n.missingCount > 0 && (
        <Panel
          title="Still needed"
          subtitle="Summed across every building site, minus what the huts and warehouse already hold"
          count={n.missingCount}
        >
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2.5">
            {n.neededItems.map(item => (
              <ItemTooltip
                key={item.itemKey}
                item={item}
                defaultExpanded
                lines={
                  <div className="mc-line">
                    Needed <span className="mc-val">{num(item.needed)}</span> ·
                    Huts <span className="mc-val">{num(item.inHut)}</span> ·
                    Warehouse <span className="mc-val">{num(item.inWarehouse)}</span> ·
                    {' '}{item.sites === 1 ? '1 site' : `${item.sites} sites`}
                  </div>
                }
                right={
                  <div className="text-right shrink-0">
                    <div className="mc-count">{stacks(item.shortfall, item.maxStackSize)}</div>
                    <span className="pill missing mt-1.5">Short</span>
                  </div>
                }
              />
            ))}
          </div>

          {!n.neededItems.length && (
            <div className="empty"><p className="empty-title">No missing items match your search.</p></div>
          )}
        </Panel>
      )}

      {!n.missingCount && (
        <div className="empty">
          <p className="empty-title">
            {n.anyRequirements
              ? 'Every builder has what they need.'
              : 'Nothing is being built right now.'}
          </p>
          <p className="mt-1">
            {n.anyRequirements
              ? 'Anything still outstanding is already in a hut or waiting in the warehouse.'
              : 'Start a build or an upgrade and whatever it is short of will show up here.'}
          </p>
        </div>
      )}
    </div>
  )
}
