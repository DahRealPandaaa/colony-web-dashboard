import { textureUrl } from '../../api'
import Craft from '../icons/Craft'
import type { ItemCount } from '../../types/item'

/** One "48 × Diamond" entry under a research card. */
export default function ResearchCostItem({ item }: { item: ItemCount }) {
  const title = item.name + (item.craftable ? ' — the colony can craft this' : '')

  return (
    <span className="inline-flex items-center gap-1 text-xs text-slate-400" title={title}>
      <img className="w-4 h-4 pixelated" loading="lazy" src={textureUrl(item.itemKey)} alt={item.name} />
      <b className="tabular-nums">{item.count}</b>
      {item.craftable && (
        <span className="craft-mark w-3! h-3!">
          <Craft size={8} strokeWidth={3} />
        </span>
      )}
    </span>
  )
}
