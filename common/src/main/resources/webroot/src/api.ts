import type { ColonySummary, ColonySnapshot } from './types/colony'
import type { CombatInfo } from './types/combat'
import type { MapInfo } from './types/map'
import type { ResearchInfo } from './types/research'
import type { CitizenInfo, CitizenDetail } from './types/citizen'
import type { SessionResponse, LoginResult } from './types/auth'

class Unauthorized extends Error {
  constructor() {
    super('Not signed in')
    this.name = 'Unauthorized'
  }
}

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { cache: 'no-store' })
  if (res.status === 401) throw new Unauthorized()
  if (!res.ok) throw new Error(`${url} -> ${res.status}`)
  return res.json()
}

async function postForm(url: string, body?: Record<string, string>): Promise<LoginResult> {
  const formBody = body
    ? Object.entries(body).map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&')
    : ''
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: formBody,
  })
  let data: Record<string, unknown> = {}
  try { data = await res.json() } catch { /* non-JSON body */ }
  return { ok: res.ok, status: res.status, data } as LoginResult
}

export function fetchSession(): Promise<SessionResponse> {
  return getJson<SessionResponse>('/auth/me')
}

export function loginWithCode(code: string): Promise<LoginResult> {
  return postForm('/auth/login', { code })
}

export function logoutSession(): Promise<LoginResult> {
  return postForm('/auth/logout')
}

export function fetchColonies(): Promise<ColonySummary[]> {
  return getJson<ColonySummary[]>('/api/colonies')
}

export function fetchColonySnapshot(colonyId: number): Promise<ColonySnapshot> {
  return getJson<ColonySnapshot>(`/api/colony/${colonyId}`)
}

export function fetchColonySection<T>(colonyId: number, section: string): Promise<T> {
  return getJson<T>(`/api/colony/${colonyId}/${section}`)
}

export function fetchCitizenDetail(colonyId: number, citizenId: number): Promise<CitizenDetail> {
  return getJson<CitizenDetail>(`/api/colony/${colonyId}/citizen/${citizenId}`)
}

const RENDER_VERSION = '3'

export function textureUrl(key: string): string {
  return `/textures/${encodeURIComponent(key)}.png?v=${RENDER_VERSION}`
}

export { Unauthorized }
