package DahRealPanda.plugins.colonyweb.neoforge;

import DahRealPanda.plugins.colonyweb.platform.ItemSlots;
import DahRealPanda.plugins.colonyweb.platform.Platform;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** {@link Platform} for Minecraft 1.21.1 on NeoForge. */
public final class NeoForgePlatform implements Platform {

    @Override
    public boolean isModLoaded(String modId) {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(modId);
    }

    @Override
    public List<String> loadedModIds() {
        ModList mods = ModList.get();
        return mods == null ? List.of() : mods.getMods().stream().map(mod -> mod.getModId()).toList();
    }

    @Override
    public Path serverDataDir(MinecraftServer server, String name) {
        return server.getServerDirectory().resolve(name);
    }

    @Override
    public Optional<ItemSlots> itemSlots(BlockEntity blockEntity) {
        // 1.20.5 moved capabilities off the block entity: they are now looked up on the level at
        // a position, which is why this takes the block entity and unpicks it rather than being
        // handed a level and a pos.
        Level level = blockEntity.getLevel();
        if (level == null) {
            return Optional.empty();
        }
        IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(),
                blockEntity, null);
        return Optional.ofNullable(handler).map(NeoForgePlatform::wrap);
    }

    @Override
    public Optional<ItemSlots> asItemSlots(Object handler) {
        return handler instanceof IItemHandler items ? Optional.of(wrap(items)) : Optional.empty();
    }

    @Override
    public Optional<CompoundTag> blockEntityData(ItemStack stack) {
        // 1.20.5 replaced stack NBT with typed components. A placed-block item keeps what used to
        // be BlockEntityTag in the block_entity_data component, which is where Domum Ornamentum's
        // textureData compound now lives.
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? Optional.empty() : Optional.of(data.copyTag());
    }

    @Override
    public String dataFingerprint(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            return data.copyTag().toString();
        }
        // Should a future Domum Ornamentum keep its materials in a component of its own instead,
        // the patch still tells two variants apart. Ordering is only best-effort here, and the
        // cost of a key changing between runs is one texture rendered again.
        var patch = stack.getComponentsPatch();
        return patch.isEmpty() ? null : patch.toString();
    }

    @Override
    public int armorPoints(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor ? armor.getDefense() : 0;
    }

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    private static ItemSlots wrap(IItemHandler handler) {
        return new ItemSlots() {
            @Override
            public int getSlots() {
                return handler.getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return handler.getStackInSlot(slot);
            }
        };
    }
}
