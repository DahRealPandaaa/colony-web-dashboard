/**
 * The signed-in player, as `/auth/me` and `/auth/login` return them.
 *
 * The server serialises its whole `WebUser` record, so access is two separate lists: colonies
 * mirrored from MineColonies by `/colonyweb sync`, and colonies an operator granted by hand.
 */
export interface WebUser {
  uuid: string
  name: string
  /** Colonies the player belongs to in-game. */
  colonies: number[]
  /** Colonies an operator granted explicitly. */
  granted: number[]
  /** Server operators see every colony. */
  admin: boolean
  syncedAt: number
}

export interface SessionResponse {
  authenticated: boolean
  authEnabled: boolean
  user?: WebUser | null
}

/** POST results are reported as data rather than thrown, so the form can show the message. */
export interface LoginResult {
  ok: boolean
  status: number
  data: {
    authenticated?: boolean
    error?: string
    user?: WebUser
  }
}

/** How many colonies a user may view, across both access lists. */
export function colonyCount(user: WebUser | null): number {
  if (!user) return 0
  const all = new Set<number>([...(user.colonies || []), ...(user.granted || [])])
  return all.size
}
