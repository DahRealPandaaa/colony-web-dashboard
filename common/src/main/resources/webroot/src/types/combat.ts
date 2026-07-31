import type { EquipmentInfo } from './citizen'

export interface Guard {
  id: number
  name: string
  job: string
  jobType: string
  /** Job-relevant skill level. */
  level: number
  health: number
  maxHealth: number
  spawned: boolean
  /** The guard post they are stationed at. */
  building: string
  buildingId: number
  buildingLevel: number
  equipment: EquipmentInfo[]
  /** Total vanilla armour value across the four slots. */
  armorPoints: number
  /** Main-hand item name, null when empty-handed. */
  weapon: string | null
  x: number
  y: number
  z: number
}

/** A guard tower, barracks or barracks tower. */
export interface Post {
  id: number
  name: string
  type: string
  blockId: string
  level: number
  assigned: number
  capacity: number
  x: number
  y: number
  z: number
}

/** A raid or other colony event currently running. */
export interface CombatEvent {
  id: number
  name: string
  status: string
  x: number
  y: number
  z: number
}

export interface CombatInfo {
  raidsPossible: boolean
  underAttack: boolean
  nightsSinceRaid: number
  /** MineColonies' colony raid level (raid difficulty scaling). */
  raidLevel: number
  spiesEnabled: boolean
  guardCount: number
  /** Total guard slots across guard buildings. */
  guardCapacity: number
  averageGuardLevel: number
  averageHealthPct: number
  /** Unclaimed graves — citizens that died and need burying. */
  graves: number
  guards: Guard[]
  posts: Post[]
  events: CombatEvent[]
}
