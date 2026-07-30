import { textureUrl } from '../../api'
import type { EquipmentInfo } from '../../types/citizen'

/** The tooltip a gear slot carries: what it is, what it adds, and how worn it is. */
function gearTitle(item: EquipmentInfo): string {
  const armour = item.armorPoints ? ` (+${item.armorPoints} armour)` : ''
  const wear = item.durabilityPct < 100 ? ` · ${item.durabilityPct}% durability` : ''
  return `${item.slot}: ${item.name}${armour}${wear}`
}

interface Props {
  equipment: EquipmentInfo[]
}

/** A citizen's or guard's equipped items, as a row of inventory-style slots. */
export default function EquipmentStrip({ equipment }: Props) {
  return (
    <>
      {equipment.map((item, i) => (
        <span key={i} className={`gear${item.enchanted ? ' ench' : ''}`} title={gearTitle(item)}>
          <img className="pixelated w-full h-full" loading="lazy"
            src={textureUrl(item.itemKey)} alt={item.name} />
        </span>
      ))}
    </>
  )
}
