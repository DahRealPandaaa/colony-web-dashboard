package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.EquipmentInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAny;

/**
 * Reads what a citizen is wearing and holding.
 *
 * <p>A loaded citizen is a {@link LivingEntity}, so vanilla's equipment slots answer directly.
 * An unloaded one only exists as data, and MineColonies keeps their armour on the citizen's
 * own inventory — hence the second path.</p>
 */
public final class EquipmentScanner {

    /** Slots we report, in the order the UI shows them. */
    private static final Map<EquipmentSlot, String> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put(EquipmentSlot.HEAD, "Head");
        SLOTS.put(EquipmentSlot.CHEST, "Chest");
        SLOTS.put(EquipmentSlot.LEGS, "Legs");
        SLOTS.put(EquipmentSlot.FEET, "Feet");
        SLOTS.put(EquipmentSlot.MAINHAND, "Main hand");
        SLOTS.put(EquipmentSlot.OFFHAND, "Off hand");
    }

    private EquipmentScanner() {
    }

    /** Everything a citizen has equipped; empty when nothing could be read. */
    public static List<EquipmentInfo> read(Object citizen) {
        LivingEntity entity = livingEntity(citizen);
        Object inventory = invokeAny(citizen, "getInventory").orElse(null);

        List<EquipmentInfo> out = new ArrayList<>();
        for (Map.Entry<EquipmentSlot, String> slot : SLOTS.entrySet()) {
            ItemStack stack = stackFor(entity, inventory, slot.getKey());
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            out.add(describe(stack, slot.getValue()));
        }
        return out;
    }

    private static LivingEntity livingEntity(Object citizen) {
        Object entity = invokeAny(citizen, "getEntity").orElse(null);
        if (entity instanceof java.util.Optional<?> optional) {
            entity = optional.orElse(null);
        }
        return entity instanceof LivingEntity living ? living : null;
    }

    private static ItemStack stackFor(LivingEntity entity, Object inventory, EquipmentSlot slot) {
        if (entity != null) {
            ItemStack worn = entity.getItemBySlot(slot);
            if (worn != null && !worn.isEmpty()) {
                return worn;
            }
        }
        // Unloaded citizen: MineColonies keeps armour on the citizen's own inventory.
        return Scan.itemStackOf(Scan.firstNonNull(
                invokeAny(inventory, "getArmorInSlot", slot).orElse(null),
                slot == EquipmentSlot.MAINHAND
                        ? invokeAny(inventory, "getHeldItemMainhand").orElse(null) : null,
                slot == EquipmentSlot.OFFHAND
                        ? invokeAny(inventory, "getHeldItemOffhand").orElse(null) : null));
    }

    private static EquipmentInfo describe(ItemStack stack, String slotName) {
        EquipmentInfo info = Scan.fillItem(new EquipmentInfo(), stack);
        info.slot = slotName;
        info.enchanted = stack.isEnchanted();

        if (stack.getItem() instanceof ArmorItem armor) {
            info.armorPoints = armor.getDefense();
        }
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            info.durabilityPct = (int) Math.round(
                    100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
        }
        return info;
    }

}
