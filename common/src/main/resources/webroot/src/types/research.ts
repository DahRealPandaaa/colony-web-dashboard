import type { ItemCount } from './item'

export type ResearchState = 'COMPLETED' | 'IN_PROGRESS' | 'NOT_STARTED'

export interface ResearchEntry {
  id: string
  name: string
  /** Readable name of the branch this sits in. */
  branch: string
  /** Tier within the branch. */
  depth: number
  state: ResearchState
  progress: number
  maxProgress: number
  effects: string[]
  requirements: string[]
  cost: ItemCount[]
}

export interface ResearchBranch {
  id: string
  name: string
  completed: number
  inProgress: number
  total: number
  researches: ResearchEntry[]
}

export interface ResearchInfo {
  branches: ResearchBranch[]
  completed: number
  inProgress: number
  total: number
  /** False when MineColonies exposes no research tree. */
  available: boolean
}
