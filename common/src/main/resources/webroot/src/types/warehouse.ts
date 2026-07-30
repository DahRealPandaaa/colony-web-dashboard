import type { ItemInfo } from './item'

/** One aggregated stock line: every copy of an item across the colony, collapsed. */
export interface WarehouseStack extends ItemInfo {
  count: number
  /** Maximum items per stack for this specific item. */
  maxStackSize: number
}

export interface WarehouseInfo {
  present: boolean
  stacks: WarehouseStack[]
}
