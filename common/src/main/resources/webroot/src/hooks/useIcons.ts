import { textureUrl } from '../api'
import type { BuildingInfo } from '../types/building'
import type { CitizenInfo } from '../types/citizen'
import type { Post } from '../types/combat'

/**
 * Artwork for buildings and citizens.
 *
 * The mod ships MineColonies wiki assets under /img/: the game's own block renders for
 * buildings and decorations, and worker portraits for citizens. Not every block or job has art,
 * and the browser is the first to find out — so each <img> walks a fallback chain on error,
 * ending at a bundled placeholder that cannot itself fail. Licensing: img/ATTRIBUTION.md.
 */

/** Shown when every other candidate 404s, so a card never renders a broken image. */
const PLACEHOLDER = '/img/placeholder.svg'

/** Decorations are anchored by a deco controller rather than a hut block. */
const DECORATION_BLOCK = '/img/blocks/decorationcontroller.png'

/**
 * Building types whose hut block is named differently.
 *
 * Only used when the building carries no block id — normally the block read from the world
 * answers directly and none of this matters.
 */
const HUT_ALIASES: Record<string, string> = {
  home: 'citizen',
  residence: 'citizen',
  beekeeping: 'beekeeper',
  cookery: 'cook',
  restaurant: 'cook',
}

/** Anything with a hut block behind it: a building, a decoration, or a guard post. */
export interface Placeable {
  blockId?: string | null
  type?: string
  kind?: string
  required?: { itemKey: string }[]
}

/** The path part of a `namespace:path` id, stripped to what a filename can hold. */
function pathOf(id?: string | null): string {
  return String(id || '').split(':').pop()!.replace(/[^a-z0-9_]/gi, '').toLowerCase()
}

interface IconCandidate {
  url: string
  pixelated?: boolean
}

/**
 * Walk an image through its remaining candidates, one per error.
 *
 * Server-rendered block textures are 16px art that must not be smoothed, so `pixelated` goes on
 * only for those; the placeholder and the wiki renders want normal filtering.
 */
function stepIcon(el: HTMLImageElement, candidates: IconCandidate[]) {
  const step = Number(el.dataset.step || 0)
  const next = candidates[step]
  el.dataset.step = String(step + 1)
  if (next === undefined) return
  el.classList.toggle('pixelated', next.pixelated === true)
  el.src = next.url
}

/**
 * The block render for a building or decoration.
 *
 * Prefers the block actually placed in the world — every MineColonies block is bundled, so a
 * hut, a quarry or a deco controller all resolve the same way. Falls back to deriving a hut
 * name from the building type.
 */
export function buildingArt(building: Placeable): string {
  const block = pathOf(building.blockId)
  if (block && String(building.blockId).startsWith('minecolonies:')) {
    return `/img/blocks/${block}.png`
  }
  if (building.kind === 'decoration') return DECORATION_BLOCK
  const type = pathOf(building.type)
  if (type === 'decoration') return DECORATION_BLOCK
  return type ? `/img/blocks/blockhut${HUT_ALIASES[type] || type}.png` : DECORATION_BLOCK
}

/** Prefer the real MineColonies hut block placed at the site, rendered by the server. */
export function buildingIcon(building: Placeable): string {
  if (building.blockId) return textureUrl(building.blockId)
  if (building.kind === 'decoration') {
    const first = (building.required || [])[0]
    return textureUrl(first ? first.itemKey : 'minecolonies:blockhutbuilder')
  }
  return textureUrl('minecolonies:blockhut' + pathOf(building.type))
}

/** Wiki portrait for a citizen, by job and gender. */
export function citizenArt(citizen: CitizenInfo): string {
  const gender = citizen.female ? 'female' : 'male'
  const job = pathOf(citizen.jobType)
  return job ? `/img/jobs/${job}-${gender}.png` : citizenArtFallback(citizen)
}

/** The generic settler portrait, for anyone unemployed or with no job art. */
export function citizenArtFallback(citizen: CitizenInfo): string {
  return `/img/jobs/_citizen-${citizen.female ? 'female' : 'male'}.png`
}

export function citizenIcon(citizen: CitizenInfo): string {
  return textureUrl(citizen.jobIcon || 'minecolonies:blockhuttownhall')
}

/** Buildings: wiki block render -> server texture -> placeholder. */
export function buildingIconFallback(el: HTMLImageElement, building: Placeable) {
  stepIcon(el, [
    { url: buildingIcon(building), pixelated: true },
    { url: PLACEHOLDER },
  ])
}

/** Citizens: job portrait -> generic settler -> job hut texture -> placeholder. */
export function citizenIconFallback(el: HTMLImageElement, citizen: CitizenInfo) {
  stepIcon(el, [
    { url: citizenArtFallback(citizen) },
    { url: citizenIcon(citizen), pixelated: true },
    { url: PLACEHOLDER },
  ])
}

/** Guard posts, which have a block id but no BuildingInfo behind them. */
export function postIconFallback(el: HTMLImageElement, post: Post) {
  stepIcon(el, [
    { url: textureUrl(post.blockId || 'minecolonies:blockhutguardtower'), pixelated: true },
    { url: PLACEHOLDER },
  ])
}

export type { BuildingInfo }
