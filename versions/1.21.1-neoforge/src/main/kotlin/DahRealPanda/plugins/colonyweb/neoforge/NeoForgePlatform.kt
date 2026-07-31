package DahRealPanda.plugins.colonyweb.neoforge

import DahRealPanda.plugins.colonyweb.platform.ItemSlots
import DahRealPanda.plugins.colonyweb.platform.Platform
import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.fml.ModList
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import java.nio.file.Path

/**
 * [Platform] for Minecraft 1.21.1 on NeoForge.
 */
class NeoForgePlatform : Platform {

    override fun isModLoaded(modId: String): Boolean {
        val mods = ModList.get()
        return mods != null && mods.isLoaded(modId)
    }

    override fun loadedModIds(): List<String> {
        val mods = ModList.get()
        return mods?.mods?.map { it.modId } ?: emptyList()
    }

    override fun serverDataDir(server: MinecraftServer, name: String): Path {
        return server.serverDirectory.resolve(name)
    }

    override fun itemSlots(blockEntity: BlockEntity): ItemSlots? {
        // 1.20.5 moved capabilities off the block entity: they are now looked up on the level at
        // a position, which is why this takes the block entity and unpicks it rather than being
        // handed a level and a pos.
        val level = blockEntity.level ?: return null
        val handler = level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            blockEntity.blockPos,
            blockEntity.blockState,
            blockEntity,
            null
        )
        return handler?.let { wrap(it) }
    }

    override fun asItemSlots(handler: Any?): ItemSlots? {
        return if (handler is IItemHandler) wrap(handler) else null
    }

    override fun blockEntityData(stack: ItemStack): CompoundTag? {
        // 1.20.5 replaced stack NBT with typed components. A placed-block item keeps what used to
        // be BlockEntityTag in the block_entity_data component, which is where Domum Ornamentum's
        // textureData compound now lives.
        val data = stack.get(DataComponents.BLOCK_ENTITY_DATA)
        return data?.copyTag()
    }

    override fun dataFingerprint(stack: ItemStack): String? {
        val data = stack.get(DataComponents.BLOCK_ENTITY_DATA)
        if (data != null) {
            return data.copyTag().toString()
        }
        // Should a future Domum Ornamentum keep its materials in a component of its own instead,
        // the patch still tells two variants apart. Ordering is only best-effort here, and the
        // cost of a key changing between runs is one texture rendered again.
        val patch = stack.componentsPatch
        return if (patch.isEmpty) null else patch.toString()
    }

    override fun blockStateProperty(stack: ItemStack, property: String): String? {
        return stack.get(DataComponents.BLOCK_STATE)?.properties()?.get(property)
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
