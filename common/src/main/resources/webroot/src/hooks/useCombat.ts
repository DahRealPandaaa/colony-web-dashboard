import { useColony } from '../contexts/ColonyContext'
import type { Guard, Post } from '../types/combat'

/**
 * Combat tab helpers. The payload is already summarised server-side, so this only handles
 * presentation decisions.
 */
export function useCombat() {
  const { combat } = useColony()

  /** Staffing indicator colour for a guard post. */
  const postStatus = (post: Post): 'ok' | 'deliver' | 'missing' => {
    if (post.assigned >= post.capacity) return 'ok'
    return post.assigned > 0 ? 'deliver' : 'missing'
  }

  const guardHealthPct = (guard: Guard) =>
    (guard.maxHealth ? (guard.health / guard.maxHealth) * 100 : 0)

  const raidHeadline = (() => {
    if (!combat) return ''
    if (combat.underAttack) return 'The colony is under attack'
    return combat.raidsPossible ? 'No active raid' : 'Raids are disabled for this colony'
  })()

  return { combat, postStatus, guardHealthPct, raidHeadline }
}
