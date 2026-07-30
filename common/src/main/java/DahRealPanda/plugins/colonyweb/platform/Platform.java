package DahRealPanda.plugins.colonyweb.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The handful of things that differ between the Minecraft versions and mod loaders ColonyWeb
 * supports.
 *
 * <p>Almost all of the mod compiles unchanged against every target: it registers nothing, has no
 * mixins, no client code, and reaches MineColonies purely through reflection. What is left is
 * this interface — item handlers, stack NBT, mod discovery — implemented once per
 * {@code versions/<mc>-<loader>} project.</p>
 *
 * <p><strong>Adding a Minecraft version:</strong> implement this, and nothing in {@code common/}
 * should need to change. If a port needs something that is not here, add a method rather than
 * moving the calling class out of {@code common/} — that keeps the shared surface honest about
 * how much really differs.</p>
 */
public interface Platform {

    /** @return true when a mod with this id is present in the runtime. */
    boolean isModLoaded(String modId);

    /**
     * Every loaded mod id. Used to find the language files a dedicated server does not load
     * itself, so it may be called before the mod list exists — return an empty list rather than
     * throwing in that case.
     */
    List<String> loadedModIds();

    /** A directory under the server's own directory, created on demand by the caller. */
    Path serverDataDir(MinecraftServer server, String name);

    /**
     * The item handler exposed by a block entity, if it has one.
     *
     * <p>1.20.1 Forge asks the block entity for a capability; 1.20.5 and later ask the level for
     * one at a position. Both end up here.</p>
     */
    Optional<ItemSlots> itemSlots(BlockEntity blockEntity);

    /**
     * Adapt an object obtained by reflection — a MineColonies inventory, say — to {@link
     * ItemSlots}, or empty when it is not an item handler.
     *
     * <p>Needed because the handler interface itself is loader-specific ({@code
     * net.minecraftforge.items.IItemHandler} vs {@code net.neoforged.neoforge.items.IItemHandler}),
     * so shared code cannot name it in an {@code instanceof}.</p>
     */
    Optional<ItemSlots> asItemSlots(Object handler);

    /**
     * The block-entity data carried by a stack, which is where Domum Ornamentum keeps its
     * {@code textureData} compound.
     *
     * <p>1.20.1 stores it in the stack's NBT tag; 1.20.5 and later store it in the {@code
     * minecraft:block_entity_data} component. Callers get the enclosing compound either way and
     * search it for {@code textureData}.</p>
     */
    Optional<CompoundTag> blockEntityData(ItemStack stack);

    /**
     * A block-state property a stack pins for the block it places, e.g. {@code "type"}.
     *
     * <p>Domum Ornamentum splits some families — posts, fancy doors and trapdoors — into one block
     * with a {@code type} property rather than one block per shape, and the cut shape then travels
     * on the stack. 1.20.1 keeps it in the stack's NBT; 1.20.5 and later in the {@code
     * minecraft:block_state} component.</p>
     *
     * @return the raw property value (e.g. {@code "plain"}), or empty when the stack pins none.
     */
    Optional<String> blockStateProperty(ItemStack stack, String property);

    /**
     * A stable string identifying everything about a stack beyond its item id, or null when the
     * stack carries nothing extra.
     *
     * <p>Two Domum Ornamentum blocks with the same item id can look completely different, so this
     * is what distinguishes their cached textures. It only has to be stable within one Minecraft
     * version — the texture cache is regenerated on demand.</p>
     */
    String dataFingerprint(ItemStack stack);

    /** Armour points a stack grants when worn, or 0 when it is not armour. */
    int armorPoints(ItemStack stack);

    /**
     * The running Minecraft version, e.g. {@code "1.20.1"}. Selects which vanilla client jar the
     * texture pipeline downloads for its item icons.
     */
    String minecraftVersion();

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /** The implementation for the running loader. Never null once the mod has been constructed. */
    static Platform get() {
        Platform platform = Holder.current;
        if (platform == null) {
            throw new IllegalStateException(
                    "Platform.init was never called — the loader's @Mod entrypoint must install "
                            + "its implementation before anything else runs");
        }
        return platform;
    }

    /** Called from each loader's {@code @Mod} entrypoint, before any other ColonyWeb code runs. */
    static void init(Platform platform) {
        Holder.current = platform;
    }

    /** Interface fields are implicitly final, so the mutable slot lives here. */
    final class Holder {
        private static volatile Platform current;

        private Holder() {
        }
    }
}
