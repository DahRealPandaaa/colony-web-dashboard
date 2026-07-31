import { useMemo } from 'react'
import { useColony } from '../contexts/ColonyContext'
import { useUi } from '../contexts/UiContext'
import { matches } from '../format/format'
import { hasRequirements, stillNeeded } from '../format/needed'

export function useNeeded() {
  const { snap } = useColony()
  const { neededSearch, setNeededSearch, neededSort, setNeededSort } = useUi()

  const missing = useMemo(() => stillNeeded(snap), [snap])

  const neededItems = useMemo(() => {
    const query = neededSearch.trim()
    const list = query
      ? missing.filter(item => matches(query, item.name, item.material))
      : missing.slice()

    return list.sort(neededSort === 'alpha'
      ? (a, b) => (a.name || '').localeCompare(b.name || '')
      : (a, b) => b.shortfall - a.shortfall)
  }, [missing, neededSearch, neededSort])

  /** Totals cover the whole colony, not just what the search box left on screen. */
  const neededTotal = useMemo(
    () => missing.reduce((sum, item) => sum + item.shortfall, 0),
    [missing],
  )

  const waitingSites = useMemo(
    () => (snap.buildings || []).filter(b => (b.required || []).some(r => r.inHut < r.needed)).length,
    [snap.buildings],
  )

  return {
    neededSearch, setNeededSearch, neededSort, setNeededSort,
    neededItems, neededTotal, waitingSites,
    missingCount: missing.length,
    anyRequirements: hasRequirements(snap),
  }
}
