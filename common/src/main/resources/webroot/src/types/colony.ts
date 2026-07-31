import type { BuilderInfo, WorkOrderInfo, BuildingInfo } from './building'
import type { WarehouseInfo } from './warehouse'

export interface ColonySummary {
  id: number
  name: string
  dimension: string
  owner: string
  x: number
  y: number
  z: number
  buildingCount: number
  builderCount: number
  activeWorkOrders: number
}

export interface ColonyStats {
  citizens: number
  maxCitizens: number
  children: number
  unemployed: number
  /** Colony-wide average, 0-10. */
  happiness: number
  /** Average citizen saturation, 0-20. */
  saturation: number
  buildings: number
  decorations: number
  workOrders: number
  builders: number
  guards: number
  /** Distinct stacks in the warehouse. */
  warehouseTypes: number
  /** Total item count in the warehouse. */
  warehouseItems: number
  researchCompleted: number
  researchInProgress: number
  raided: boolean
  nightsSinceRaid: number
}

export interface ColonySnapshot {
  id: number
  name: string
  dimension: string
  owner: string
  builders: BuilderInfo[]
  workOrders: WorkOrderInfo[]
  buildings: BuildingInfo[]
  warehouse: WarehouseInfo
  stats: ColonyStats
  /** Built client-side once per refresh, so rendering never scans the work-order list. */
  workOrdersById: Record<number, WorkOrderInfo>
}
