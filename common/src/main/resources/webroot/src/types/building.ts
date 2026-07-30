import type { ItemInfo } from './item'

/** One line of a building's required-resources list. */
export interface ResourceEntry extends ItemInfo {
  needed: number
  maxStackSize: number
  /** Amount already in the building's own hut inventory. */
  inHut: number
  /** Amount available across the colony warehouse(s). */
  inWarehouse: number
  /** True when the warehouse can cover the shortfall. */
  deliverable: boolean
}

export interface BuildingInfo {
  id: number
  name: string
  type: string
  /** "building" or "decoration". */
  kind: string
  /** Registry id of the MineColonies hut block, null when the site has none. */
  blockId: string | null
  level: number
  x: number
  y: number
  z: number
  beingBuilt: boolean
  /** -1 when no work order targets this building. */
  workOrderId: number
  required: ResourceEntry[]
}

export interface BuilderInfo {
  id: number
  name: string
  hutX: number
  hutY: number
  hutZ: number
  /** -1 when the builder is idle. */
  assignedWorkOrderId: number
}

export interface WorkOrderInfo {
  id: number
  buildingName: string
  buildingType: string
  x: number
  y: number
  z: number
  currentLevel: number
  targetLevel: number
  /** BUILD / UPGRADE / REPAIR / REMOVE. */
  action: string
  /** -1 when unclaimed. */
  builderId: number
  builderName: string
  /** 0.0 - 1.0. */
  progress: number
}
