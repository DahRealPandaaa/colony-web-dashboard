/**
 * Item shapes shared by every endpoint that names a Minecraft item.
 *
 * Mirrors `colony/model/ItemInfo.kt` and its subclasses: the server sends one flat object per
 * item, with the Domum Ornamentum material breakdown inline when the block has one.
 */

/** One Domum Ornamentum material slot, e.g. "Main Material: Brick Extra". */
export interface MaterialComponent {
  id: string
  label: string
  material: string
  itemKey: string
}

/** The base every item payload extends. */
export interface ItemInfo {
  itemKey: string
  name: string
  /** Combined DO material names, null when the item is not a Domum block. */
  material: string | null
  domum: boolean
  /** e.g. "Architects Cutter", null when unknown. */
  craftedIn: string | null
  /** A colony worker knows a recipe that produces this. */
  craftable: boolean
  components: MaterialComponent[]
}

/** An item with a count, and optionally the inventory slot it sits in (-1 when unbound). */
export interface ItemCount extends ItemInfo {
  count: number
  slot: number
}
