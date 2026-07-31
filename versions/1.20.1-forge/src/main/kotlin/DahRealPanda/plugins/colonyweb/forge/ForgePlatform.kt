package DahRealPanda.plugins.colonyweb.forge

import DahRealPanda.plugins.colonyweb.platform.ItemSlots
import DahRealPanda.plugins.colonyweb.platform.Platform
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.fml.ModList
import net.minecraftforge.items.IItemHandler
import java.nio.file.Path

/**
 * [Platform] for Minecraft 1.20.1 on Forge.
 */
class ForgePlatform : Platform {

    override fun isModLoaded(modId: String): Boolean {
        val mods = ModList.get()
        return mods != null && mods.isLoaded(modId)
    }

    override fun loadedModIds(): List<String> {
        val mods = ModList.get()
        return mods?.mods?.map { it.modId } ?: emptyList()
    }

    override fun serverDataDir(server: MinecraftServer, name: String): Path {
        // 1.21.4 changed this to return a Path directly, which is the whole reason it is here.
        return server.serverDirectory.toPath().resolve(name)
    }

    override fun itemSlots(blockEntity: BlockEntity): ItemSlots? {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
            .resolve()
            .map { handler -> wrap(handler) }
            .orElse(null)
    }

    override fun asItemSlots(handler: Any?): ItemSlots? {
        return if (handler is IItemHandler) wrap(handler) else null
    }

    override fun blockEntityData(stack: ItemStack): CompoundTag? {
        // Pre-1.20.5 everything a stack carries lives in one tag; the caller searches it for the
        // textureData compound, wherever inside it happens to sit.
        return stack.tag
    }

    override fun dataFingerprint(stack: ItemStack): String? {
        return if (stack.hasTag()) stack.tag.toString() else null
    }

    override fun blockStateProperty(stack: ItemStack, property: String): String? {
        val tag = stack.tag ?: return null
        val fromBlockState = tag.getCompound("BlockStateTag").getString(property)
        if (fromBlockState.isNotEmpty()) return fromBlockState
        // Domum Ornamentum writes the shape at the top level on some of its own item stacks.
        return tag.getString(property).ifEmpty { null }
    }

    override fun armorPoints(stack: ItemStack): Int {
        return if (stack.item is ArmorItem) (stack.item as ArmorItem).defense else 0
    }

    override fun minecraftVersion(): String {
        return SharedConstants.getCurrentVersion().name
    }

    private fun wrap(handler: IItemHandler): ItemSlots {
        return object : ItemSlots {
            override fun getSlots(): Int = handler.slots

            override fun getStackInSlot(slot: Int): ItemStack = handler.getStackInSlot(slot)
        }
    }
}
