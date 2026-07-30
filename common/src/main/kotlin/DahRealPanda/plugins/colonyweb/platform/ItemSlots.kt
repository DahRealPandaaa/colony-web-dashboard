package DahRealPanda.plugins.colonyweb.platform

import net.minecraft.world.item.ItemStack

/**
 * A read-only view of an inventory, whatever the loader calls its item handler.
 *
 * Forge and NeoForge both have an [IItemHandler] with exactly these two methods, in
 * different packages. Shared code talks to this instead so it does not have to name either.
 */
interface ItemSlots {
    fun getSlots(): Int
    fun getStackInSlot(slot: Int): ItemStack
}
