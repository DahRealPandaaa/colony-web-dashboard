import { useMemo } from 'react'
import { useColony } from '../contexts/ColonyContext'
import { useUi } from '../contexts/UiContext'
import { matches } from '../format/format'

/** Cap on rendered rows — the search box is how you reach the long tail. */
const LIMIT = 400

/**
 * Warehouse tab: search and sort over aggregated colony stock.
 */
export function useWarehouse() {
  const { snap } = useColony()
  const { whSearch, setWhSearch, whSort, setWhSort } = useUi()

  // The hidden count is derived from the same filtered list that produced the rows, not from the
  // full stock: counting against the unfiltered list left "N more entries" on screen after a search
  // had already narrowed the results below the limit, telling the user to narrow a list that was
  // showing in full.
  const { warehouseStacks, warehouseHidden } = useMemo(() => {
    let list = (snap.warehouse.stacks || []).slice()

    const query = whSearch.trim()
    if (query) list = list.filter(s => matches(query, s.name, s.material))

    list.sort(whSort === 'alpha'
      ? (a, b) => (a.name || '').localeCompare(b.name || '')
      : (a, b) => b.count - a.count)

    return {
      warehouseStacks: list.slice(0, LIMIT),
      warehouseHidden: Math.max(0, list.length - LIMIT),
    }
  }, [snap.warehouse.stacks, whSearch, whSort])

  return { whSearch, setWhSearch, whSort, setWhSort, warehouseStacks, warehouseHidden }
}
