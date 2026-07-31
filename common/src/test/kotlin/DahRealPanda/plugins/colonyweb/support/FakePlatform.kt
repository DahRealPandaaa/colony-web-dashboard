package DahRealPanda.plugins.colonyweb.support

import DahRealPanda.plugins.colonyweb.platform.ItemSlots
import DahRealPanda.plugins.colonyweb.platform.Platform
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import java.nio.file.Path

/**
 * The loader hooks the mod actually needs, with none of the loader.
 *
 * `Platform.get()` throws until a loader's entrypoint installs an implementation, so anything
 * reaching mod detection — [DahRealPanda.plugins.colonyweb.util.MineColoniesReflect] and
 * [DahRealPanda.plugins.colonyweb.util.Translations] both do — needs one of these in place first.
 *
 * The stack-facing members are unsupported rather than stubbed: reaching them means a test has
 * wandered into code that needs a real item registry, which no unit test here sets up, and failing
 * loudly at that point is far easier to diagnose than an empty result further down.
 */
class FakePlatform(
    private val loadedMods: Set<String> = emptySet(),
    private val dataDir: Path = Path.of("build", "test-data"),
    private val version: String = "1.20.1",
) : Platform {

    override fun isModLoaded(modId: String): Boolean = modId in loadedMods

    override fun loadedModIds(): List<String> = loadedMods.toList()

    override fun serverDataDir(server: MinecraftServer, name: String): Path = dataDir.resolve(name)

    override fun itemSlots(blockEntity: BlockEntity): ItemSlots? = null

    override fun asItemSlots(handler: Any?): ItemSlots? = handler as? ItemSlots

    override fun blockEntityData(stack: ItemStack): CompoundTag? = unsupported("blockEntityData")

    override fun dataFingerprint(stack: ItemStack): String? = unsupported("dataFingerprint")

    override fun blockStateProperty(stack: ItemStack, property: String): String? =
        unsupported("blockStateProperty")

    override fun armorPoints(stack: ItemStack): Int = unsupported("armorPoints")

    override fun minecraftVersion(): String = version

    private fun unsupported(member: String): Nothing = throw UnsupportedOperationException(
        "FakePlatform.$member was called — this test reached code that needs a real ItemStack"
    )

    companion object {
        /**
         * Installs a platform for the duration of the calling spec. There is nothing to restore
         * afterwards: `Platform.current` starts unset and every test that needs one installs its
         * own, so leaving the last one behind cannot make a later test pass that would otherwise
         * fail.
         */
        fun install(vararg loadedMods: String): FakePlatform {
            val platform = FakePlatform(loadedMods.toSet())
            Platform.init(platform)
            return platform
        }

        /** A platform on which MineColonies is present, which is the normal case in production. */
        fun withMineColonies(): FakePlatform = install("minecolonies", "colonyweb")
    }
}
