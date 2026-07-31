import { useColony } from '../../contexts/ColonyContext'
import { useWarehouse } from '../../hooks/useWarehouse'
import { num, stacks } from '../../format/format'
import type { WarehouseSort } from '../../contexts/UiContext'
import ItemTooltip from '../../components/ItemTooltip/ItemTooltip'
import SearchInput from '../../components/SearchInput/SearchInput'

export function WarehouseTab() {
  const { snap, stats } = useColony()
  const w = useWarehouse()

  return (
    <div className="animate-fade-up flex flex-col gap-4">
      {/* Toolbar */}
      <div className="flex gap-2 items-center flex-wrap">
        <SearchInput className="flex-1 min-w-[160px]" value={w.whSearch} onChange={w.setWhSearch}
          placeholder="Search stock or materials…" />
        <select className="field py-2 text-xs font-semibold" aria-label="Sort stock"
          value={w.whSort} onChange={e => w.setWhSort(e.target.value as WarehouseSort)}>
          <option value="count">Most stock</option>
          <option value="alpha">Name</option>
        </select>
        <span className="chip good text-xs font-bold">{num(stats.warehouseItems)} items</span>
      </div>

      {/* Panel */}
      <div className="panel p-5!">
        <div className="flex items-start justify-between mb-4">
          <div>
            <div className="text-[15px] font-bold">Warehouse stock</div>
            <div className="text-[11px] text-text-muted mt-0.5">
              {snap.warehouse.present
                ? `${num(stats.warehouseTypes)} distinct types across all racks`
                : 'No warehouse built'}
            </div>
          </div>
          <span className="w-[22px] h-[22px] rounded-full bg-ink-800 text-xs font-bold grid place-items-center">
            {w.warehouseStacks.length}
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-2 max-h-[60vh] overflow-y-auto pr-1">
          {w.warehouseStacks.map(s => (
            <ItemTooltip
              key={s.itemKey}
              item={s}
              right={<span style={{color: '#f0a94d', fontSize: '12px', fontWeight: 700, whiteSpace: 'nowrap'}}>{stacks(s.count, s.maxStackSize)}</span>}
            />
          ))}
        </div>

        {!w.warehouseStacks.length && snap.warehouse.present && (
          <div className="empty"><p className="empty-title">No stock matches your search.</p></div>
        )}

        {w.warehouseHidden > 0 && (
          <div className="text-[10.5px] text-text-muted text-center mt-3">
            {num(w.warehouseHidden)} more entries — narrow it down with the search box.
          </div>
        )}
      </div>

      {!snap.warehouse.present && (
        <div className="empty">
          <p className="empty-title">This colony has no warehouse.</p>
        </div>
      )}
    </div>
  )
}
