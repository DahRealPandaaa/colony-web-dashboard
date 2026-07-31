import type { ItemCount, ItemInfo } from './item'

export interface Skill {
  name: string
  level: number
  xp: number
  /** "primary" / "secondary", or null when the skill is not tied to the job. */
  role: string | null
}

/** A happiness modifier: >1 is a perk, <1 a grievance. */
export interface Modifier {
  name: string
  factor: number
}

export interface CitizenInfo {
  id: number
  name: string
  /** Readable job name, "Unemployed" when idle. */
  job: string
  /** Job registry id, null when unemployed. */
  jobType: string | null
  /** Texture key for the job's hut block. */
  jobIcon: string | null
  child: boolean
  female: boolean
  health: number
  maxHealth: number
  /** 0-20. */
  saturation: number
  /** 0-10. */
  happiness: number
  /** The entity is currently loaded in the world. */
  spawned: boolean
  x: number
  y: number
  z: number
  workBuilding: string
  workBuildingId: number
  homeBuilding: string
  homeBuildingId: number
  /** Current activity, when MineColonies exposes one. */
  status: string | null
  primarySkill: string
  secondarySkill: string
  skillTotal: number
  inventoryUsed: number
  inventorySize: number
  skills: Skill[]
  modifiers: Modifier[]
}

export interface EquipmentInfo extends ItemInfo {
  /** "Head", "Chest", "Legs", "Feet", "Main hand", "Off hand". */
  slot: string
  /** Vanilla armour value, 0 for weapons and tools. */
  armorPoints: number
  enchanted: boolean
  /** 100 when undamaged or unbreakable. */
  durabilityPct: number
}

export interface CitizenDetail {
  citizen: CitizenInfo
  inventory: ItemCount[]
  equipment: EquipmentInfo[]
}
