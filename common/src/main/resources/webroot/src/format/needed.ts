import type { NeededItem } from '../types/building'
import type { ColonySnapshot } from '../types/colony'

/**
 * Every required item the colony cannot cover yet, summed across all its building sites.
 *
 * The building modal answers "what does this site still want"; this answers "what does the
 * colony have to go and get". Anything a hut already holds, or the warehouse can deliver, is
 * already somebody's job and is left out.
 */
export function stillNeeded(snap: ColonySnapshot): NeededItem[] {
  const byKey = new Map<string, NeededItem>()

  for (const building of snap.buildings || []) {
    for (const entry of building.required || []) {
      if (!entry.itemKey) continue

      const seen = byKey.get(entry.itemKey)
      if (!seen) {
        byKey.set(entry.itemKey, { ...entry, sites: 1, shortfall: 0 })
        continue
      }

      seen.needed += entry.needed
      seen.inHut += entry.inHut
      // Warehouse stock is a colony-wide count copied onto every site's copy of the entry, so
      // taking the largest counts each chest once rather than once per building.
      seen.inWarehouse = Math.max(seen.inWarehouse, entry.inWarehouse)
      seen.craftable = seen.craftable || entry.craftable
      seen.sites++
    }
  }

  const missing: NeededItem[] = []
  for (const item of byKey.values()) {
    item.shortfall = item.needed - item.inHut - item.inWarehouse
    if (item.shortfall > 0) missing.push(item)
  }
  return missing
}

/** Whether any site is waiting on anything at all, so "nothing missing" can be told from "nothing building". */
export function hasRequirements(snap: ColonySnapshot): boolean {
  return (snap.buildings || []).some(building => (building.required || []).length > 0)
}
