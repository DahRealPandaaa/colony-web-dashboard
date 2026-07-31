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
    <div className="animate-fade-up">
      <div className="toolbar">
        <SearchInput
          className="flex-1 min-w-[200px]"
          value={w.whSearch}
          onChange={w.setWhSearch}
          placeholder="Search stock or materials…"
        />
        <select className="field py-1.5" aria-label="Sort stock"
          value={w.whSort} onChange={e => w.setWhSort(e.target.value as WarehouseSort)}>
          <option value="count">Most stock</option>
          <option value="alpha">Name</option>
        </select>
        <span className="chip on ml-auto">{num(stats.warehouseItems)} items</span>
      </div>

      {/* Item cards deliberately mirror the in-game tooltip, material lines and all. */}
      <div className="grid grid-auto-items gap-2.5">
        {/* itemKey, not the array index: the list re-sorts and re-filters as you type, and an
            index key would let React reuse a card's DOM for a different item. The server
            aggregates stock into one entry per key, so it is unique within this list. */}
        {w.warehouseStacks.map(s => (
          <ItemTooltip
            key={s.itemKey}
            item={s}
            lines={!s.domum && <div className="mc-line">{s.itemKey.split('#')[0]}</div>}
            right={<div className="mc-count">{stacks(s.count, s.maxStackSize)}</div>}
          />
        ))}
      </div>

      {w.warehouseHidden > 0 && (
        <p className="text-xs text-slate-400 text-center mt-3">
          {num(w.warehouseHidden)} more entries — narrow it down with the search box.
        </p>
      )}

      {!w.warehouseStacks.length && (
        <div className="empty">
          <p className="empty-title">
            {snap.warehouse.present
              ? 'No stock matches your search.'
              : 'This colony has no warehouse.'}
          </p>
        </div>
      )}
    </div>
  )
}
