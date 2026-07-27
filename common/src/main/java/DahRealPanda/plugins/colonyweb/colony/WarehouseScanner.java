package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.texture.DomumOrnamentumResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invoke;

/**
 * Tallies warehouse stock for a colony.
 *
 * <p>Getting this right took three attempts, so the reasoning is worth keeping: MineColonies'
 * own {@code getMatchingItemStacksInWarehouse} iterates {@code getContainers()}, which lists
 * each rack more than once, and a double rack's {@code ITEM_HANDLER} capability returns a
 * combined view of <em>both</em> halves while both halves also appear in that list. Either
 * path multiplies every count. So instead each rack's own inventory is read directly, and
 * rack positions are de-duplicated across the whole colony.</p>
 *
 * <p><strong>One instance per scan.</strong> The de-duplication state below is what makes the
 * counts correct within a scan and completely wrong across scans — a reused instance considers
 * every rack already counted and reports an empty warehouse from the second scan onward.</p>
 */
public final class WarehouseScanner {
    /** Rack positions already counted, so a rack shared by two warehouses is tallied once. */
    private final Set<BlockPos> countedContainers = new HashSet<>();

    /** Aggregated stacks by texture key, so repeat visits merge into the same entry. */
    private final Map<String, ColonySnapshot.Stack> byKey = new LinkedHashMap<>();

    /** Add one warehouse building's contents to the snapshot. */
    public void addWarehouse(ServerLevel level, Object building, BlockPos hutPos,
                             ColonySnapshot.Warehouse warehouse) {
        for (IItemHandler handler : rackInventories(level, building, hutPos)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack != null && !stack.isEmpty()) {
                    add(warehouse, stack);
                }
            }
        }
    }

    private void add(ColonySnapshot.Warehouse warehouse, ItemStack stack) {
        String key = DomumOrnamentumResolver.textureKeyFor(stack);
        ColonySnapshot.Stack aggregate = byKey.get(key);
        if (aggregate == null) {
            aggregate = Scan.fillItem(new ColonySnapshot.Stack(), stack);
            aggregate.maxStackSize = Math.max(1, stack.getMaxStackSize());
            byKey.put(key, aggregate);
            warehouse.stacks.add(aggregate);
        }
        aggregate.count += stack.getCount();
    }

    /**
     * The item handlers backing a warehouse: its registered racks, or the hut block itself
     * when no containers are registered.
     */
    private List<IItemHandler> rackInventories(ServerLevel level, Object building, BlockPos hutPos) {
        List<IItemHandler> handlers = new ArrayList<>();
        if (level == null) {
            return handlers;
        }
        for (BlockPos pos : containerPositions(building, hutPos)) {
            if (!countedContainers.add(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || !isRack(blockEntity)) {
                // Skip the warehouse controller and anything else, whose capability would be a
                // combined view of every rack and would double-count.
                continue;
            }
            // Read the rack's OWN inventory, not its capability (see the class comment).
            Object ownInventory = invoke(blockEntity, "getInventory").orElse(null);
            if (ownInventory instanceof IItemHandler handler) {
                handlers.add(handler);
            } else {
                blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handlers::add);
            }
        }
        return handlers;
    }

    private Set<BlockPos> containerPositions(Object building, BlockPos hutPos) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        Object containers = invoke(building, "getContainers").orElse(null);
        if (containers instanceof Collection<?> collection) {
            for (Object entry : collection) {
                BlockPos pos = Scan.blockPosOf(entry);
                if (pos != null) {
                    positions.add(pos.immutable());
                }
            }
        }
        // Only fall back to the hut block when nothing is registered, so a controller's
        // combined view is never counted on top of its racks.
        if (positions.isEmpty()) {
            positions.add(hutPos.immutable());
        }
        return positions;
    }

    private static boolean isRack(BlockEntity blockEntity) {
        return blockEntity.getClass().getSimpleName().toLowerCase().contains("rack");
    }

    /** Total of one item across every warehouse scanned so far. */
    public static int countIn(ColonySnapshot snapshot, String itemKey) {
        int total = 0;
        for (ColonySnapshot.Stack stack : snapshot.warehouse.stacks) {
            if (stack.itemKey.equals(itemKey)) {
                total += stack.count;
            }
        }
        return total;
    }
}
