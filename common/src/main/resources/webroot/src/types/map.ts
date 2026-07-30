/**
 * Where the rendered surface image sits in world coordinates, and how much of it is drawn.
 *
 * One image pixel is one block, so a marker at block X sits at pixel `X - minX`.
 */
export interface MapInfo {
  available: boolean
  /** True once a PNG has been encoded, so /map/{colonyId}.png is expected to exist. */
  ready: boolean
  /** Why `available` is false, for the empty state. Null when it is true. */
  unavailableReason: string | null
  dimension: string
  /** The colony centre — where the town hall stands. */
  centerX: number
  centerY: number
  centerZ: number
  /** World coordinates of the image's top-left pixel. */
  minX: number
  minZ: number
  width: number
  height: number
  /** Bumped every time the image changes, so the browser can cache-bust it. */
  version: number
  renderedAt: number
  chunksMapped: number
  chunksTotal: number
}
