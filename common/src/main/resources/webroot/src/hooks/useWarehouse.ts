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

  const warehouseStacks = useMemo(() => {
    let list = (snap.warehouse.stacks || []).slice()

    const query = whSearch.trim()
    if (query) list = list.filter(s => matches(query, s.name, s.material))

    list.sort(whSort === 'alpha'
      ? (a, b) => (a.name || '').localeCompare(b.name || '')
      : (a, b) => b.count - a.count)

    return list.slice(0, LIMIT)
  }, [snap.warehouse.stacks, whSearch, whSort])

  const warehouseHidden = Math.max(0, (snap.warehouse.stacks || []).length - LIMIT)

  return { whSearch, setWhSearch, whSort, setWhSort, warehouseStacks, warehouseHidden }
}
