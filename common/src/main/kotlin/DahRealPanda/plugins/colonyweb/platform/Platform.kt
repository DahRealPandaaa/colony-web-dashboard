package DahRealPanda.plugins.colonyweb.platform

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import java.nio.file.Path

interface Platform {
    fun isModLoaded(modId: String): Boolean

    fun loadedModIds(): List<String>

    fun serverDataDir(server: MinecraftServer, name: String): Path

    fun itemSlots(blockEntity: BlockEntity): ItemSlots?

    fun asItemSlots(handler: Any?): ItemSlots?

    fun blockEntityData(stack: ItemStack): CompoundTag?

    fun dataFingerprint(stack: ItemStack): String?

    /** A block state property carried by the stack itself, e.g. the DO cut shape under "type". */
    fun blockStateProperty(stack: ItemStack, property: String): String?

    fun armorPoints(stack: ItemStack): Int

    fun minecraftVersion(): String

    companion object {
        @Volatile
        private var current: Platform? = null

        fun get(): Platform {
            return current ?: throw IllegalStateException(
                "Platform.init was never called — the loader's @Mod entrypoint must install " +
                        "its implementation before anything else runs"
            )
        }

        fun init(platform: Platform) {
            current = platform
        }
    }
}
