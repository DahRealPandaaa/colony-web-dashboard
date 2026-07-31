import { useState, type ReactNode } from 'react'
import { textureUrl } from '../../api'
import Craft from '../icons/Craft'
import type { ItemInfo } from '../../types/item'

interface Props {
  item: ItemInfo
  lines?: ReactNode
  right?: ReactNode
  defaultExpanded?: boolean
}

export default function ItemTooltip({ item, lines, right, defaultExpanded }: Props) {
  const [expanded, setExpanded] = useState(false)
  const hasMeta = !!(item.craftedIn || item.variant || (item.components || []).length > 0 || lines)
  const isExpanded = defaultExpanded || expanded

  const borderColor = item.craftable
    ? 'border-accent/40'
    : 'border-line'

  return (
    <div
      className={`card-inset flex flex-col p-2.5! ${borderColor} ${
        hasMeta && !defaultExpanded ? 'cursor-pointer' : ''
      }`}
      onClick={hasMeta && !defaultExpanded ? () => setExpanded(!expanded) : undefined}
    >
      {/* Always-visible top row: icon + name + craftable + qty + chevron */}
      <div className="flex items-center gap-2.5">
        <img
          className="w-8 h-8 object-contain pixelated rounded-md bg-black/25 border border-line p-0.5 shrink-0"
          loading="lazy"
          src={textureUrl(item.itemKey)}
          alt={item.name}
          onError={e => { (e.currentTarget as HTMLImageElement).style.display = 'none' }}
        />
        <div className="flex-1 min-w-0 flex items-center gap-1.5">
          <span className="text-[13px] font-semibold truncate text-text-primary">
            {item.name}
          </span>
          {item.craftable && (
            <span className="craft-mark shrink-0" title="A colony worker can craft this">
              <Craft />
            </span>
          )}
        </div>
        {right}
        {hasMeta && !defaultExpanded && (
          <svg
            width="13" height="13" viewBox="0 0 24 24" fill="none"
            stroke="rgba(255,255,255,.4)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
            className="shrink-0 transition-transform"
            style={{ transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
          >
            <path d="M6 9l6 6 6-6" />
          </svg>
        )}
      </div>

      {/* Expanded details: crafting info + extra lines */}
      {isExpanded && hasMeta && (
        <div className="flex flex-col gap-1 pt-2 mt-1.5 border-t border-line">
          {item.variant && (
            <div className="text-[10.5px] text-text-muted">
              Type: <span className="text-text-secondary font-semibold">{item.variant}</span>
            </div>
          )}
          {item.craftedIn && (
            <div className="text-[10.5px] text-text-muted">
              Crafted in the <span className="text-text-secondary font-semibold">{item.craftedIn}</span>
            </div>
          )}
          {(item.components || []).map((c, i) => (
            <div className="text-[10.5px] text-text-muted" key={i}>
              {c.label}: <span className="text-text-secondary font-semibold">{c.material}</span>
            </div>
          ))}
          {lines}
        </div>
      )}
    </div>
  )
}
