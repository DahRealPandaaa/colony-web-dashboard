import { useMemo } from 'react'
import { useColony } from '../contexts/ColonyContext'
import { useUi } from '../contexts/UiContext'
import { matches } from '../format/format'
import { textureUrl } from '../api'
import type { CitizenInfo, EquipmentInfo, Skill } from '../types/citizen'

/**
 * Citizens tab: the roster, its filters, and the per-citizen detail modal (skills, perks and
 * inventory). Inventories are fetched on demand by the colony store, so the roster stays small.
 */
export function useCitizens() {
  const {
    citizens, citizen, citizenInventory, citizenEquipment, openCitizen, closeCitizen,
  } = useColony()
  const {
    citizenSearch, setCitizenSearch,
    citizenJob, setCitizenJob,
    citizenSort, setCitizenSort,
  } = useUi()

  const citizenJobs = useMemo(() => {
    const jobs = new Set<string>()
    citizens.forEach(c => { if (c.job) jobs.add(c.job) })
    return Array.from(jobs).sort()
  }, [citizens])

  const visibleCitizens = useMemo(() => {
    let list = citizens.slice()
    if (citizenJob) list = list.filter(c => c.job === citizenJob)

    const query = citizenSearch.trim()
    if (query) {
      list = list.filter(c => matches(query, c.name, c.job, c.workBuilding, c.homeBuilding))
    }

    return list.sort((a, b) => {
      if (citizenSort === 'name') return (a.name || '').localeCompare(b.name || '')
      if (citizenSort === 'skills') return b.skillTotal - a.skillTotal
      if (citizenSort === 'happiness') return b.happiness - a.happiness
      if (citizenSort === 'health') {
        return (a.health / (a.maxHealth || 1)) - (b.health / (b.maxHealth || 1))
      }
      return (a.job || '').localeCompare(b.job || '')
        || (a.name || '').localeCompare(b.name || '')
    })
  }, [citizens, citizenJob, citizenSearch, citizenSort])

  /** The citizen's job skills, or their three best when they have no job. */
  const topSkills = (c: CitizenInfo): Skill[] => {
    const roled = (c.skills || []).filter(s => s.role)
    if (roled.length) return roled
    return (c.skills || []).slice().sort((a, b) => b.level - a.level).slice(0, 3)
  }

  const citizenIcon = (c: CitizenInfo) => textureUrl(c.jobIcon || 'minecolonies:blockhuttownhall')

  const healthPct = (c: CitizenInfo) => (c.maxHealth ? (c.health / c.maxHealth) * 100 : 0)

  /** Total vanilla armour value across a set of equipped items. */
  const armorPointsOf = (equipment: EquipmentInfo[]) =>
    (equipment || []).reduce((sum, item) => sum + (item.armorPoints || 0), 0)

  return {
    citizenSearch, setCitizenSearch,
    citizenJob, setCitizenJob,
    citizenSort, setCitizenSort,
    citizenJobs, visibleCitizens,
    citizen, citizenInventory, citizenEquipment, openCitizen, closeCitizen,
    topSkills, citizenIcon, healthPct, armorPointsOf,
  }
}
