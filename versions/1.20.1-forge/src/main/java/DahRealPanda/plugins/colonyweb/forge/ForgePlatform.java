package DahRealPanda.plugins.colonyweb.forge;

import DahRealPanda.plugins.colonyweb.platform.ItemSlots;
import DahRealPanda.plugins.colonyweb.platform.Platform;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** {@link Platform} for Minecraft 1.20.1 on Forge. */
public final class ForgePlatform implements Platform {

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
        // 1.21.4 changed this to return a Path directly, which is the whole reason it is here.
        return server.getServerDirectory().toPath().resolve(name);
    }

    @Override
    public Optional<ItemSlots> itemSlots(BlockEntity blockEntity) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().map(ForgePlatform::wrap);
    }

    @Override
    public Optional<ItemSlots> asItemSlots(Object handler) {
        return handler instanceof IItemHandler items ? Optional.of(wrap(items)) : Optional.empty();
    }

    @Override
    public Optional<CompoundTag> blockEntityData(ItemStack stack) {
        // Pre-1.20.5 everything a stack carries lives in one tag; the caller searches it for the
        // textureData compound, wherever inside it happens to sit.
        return Optional.ofNullable(stack.getTag());
    }

    @Override
    public Optional<String> blockStateProperty(ItemStack stack, String property) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return Optional.empty();
        }
        // Vanilla pins block-state properties under BlockStateTag; Domum Ornamentum writes the cut
        // shape of its property-based families (posts, fancy doors) as a plain string beside it.
        String fromState = tag.getCompound("BlockStateTag").getString(property);
        String value = fromState.isEmpty() ? tag.getString(property) : fromState;
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    @Override
    public String dataFingerprint(ItemStack stack) {
        return stack.hasTag() ? String.valueOf(stack.getTag()) : null;
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
