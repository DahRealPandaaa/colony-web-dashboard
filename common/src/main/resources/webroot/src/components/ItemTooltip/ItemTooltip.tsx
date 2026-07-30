import type { ReactNode } from 'react'
import { textureUrl } from '../../api'
import Craft from '../icons/Craft'
import type { ItemInfo } from '../../types/item'

interface Props {
  item: ItemInfo
  /** Extra lines below the Domum material breakdown. */
  lines?: ReactNode
  /** The right-hand column: a count, and optionally a status pill. */
  right?: ReactNode
}

/**
 * One item, drawn to deliberately mirror the in-game tooltip — material lines and all.
 *
 * Shared by the building requirements list, a citizen's inventory and the warehouse grid, which
 * all receive the same `ItemInfo` shape from the server.
 */
export default function ItemTooltip({ item, lines, right }: Props) {
  return (
    <div className="mc-tip flex items-center gap-3">
      <img className="mc-icon" loading="lazy" src={textureUrl(item.itemKey)} alt={item.name} />
      <div className="flex-1 min-w-0">
        <div className="mc-head">
          <div className="mc-name">{item.name}</div>
          {item.craftable && (
            <span className="craft-mark" title="A colony worker can craft this">
              <Craft />
            </span>
          )}
        </div>
        {item.craftedIn && (
          <div className="mc-line">Crafted in the <span className="mc-val">{item.craftedIn}</span></div>
        )}
        {(item.components || []).map((c, i) => (
          <div className="mc-line" key={i}>
            <span>{c.label}</span>: <span className="mc-val">{c.material}</span>
          </div>
        ))}
        {lines}
      </div>
      {right}
    </div>
  )
}
