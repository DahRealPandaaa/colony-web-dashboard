package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.colony.model.ItemCount;
import DahRealPanda.plugins.colonyweb.colony.model.ItemInfo;
import DahRealPanda.plugins.colonyweb.texture.DomumOrnamentumResolver;
import DahRealPanda.plugins.colonyweb.util.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Coercion helpers shared by the colony scanners. Reflection hands back {@link Object}s of
 * unknown shape, so every read goes through one of these with a safe default.
 */
public final class Scan {
    /** Domum Ornamentum blocks are all made at the architect's cutter. */
    private static final String DO_WORKSTATION = "Architects Cutter";

    private Scan() {
    }

    public static Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    public static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    public static boolean boolOf(Object o, boolean def) {
        return o instanceof Boolean b ? b : def;
    }

    public static String stringOf(Object o, String def) {
        if (o == null) {
            return def;
        }
        String s = String.valueOf(o);
        return s.isEmpty() ? def : s;
    }

    public static BlockPos blockPosOf(Object o) {
        return o instanceof BlockPos ? (BlockPos) o : null;
    }

    public static ItemStack itemStackOf(Object o) {
        return o instanceof ItemStack ? (ItemStack) o : null;
    }

    /**
     * Fill in an item's identity: texture key, display name and — for Domum Ornamentum
     * blocks — the material breakdown the UI renders as tooltip lines.
     */
    public static <T extends ItemInfo> T fillItem(T target, ItemStack stack) {
        target.itemKey = DomumOrnamentumResolver.textureKeyFor(stack);
        // Domum Ornamentum builds its names out of translatable components, and a dedicated server
        // loads no modded language files — getString() would leave the raw key behind.
        target.name = Text.componentString(stack.getHoverName());
        target.domum = DomumOrnamentumResolver.isDomum(stack);
        if (target.domum) {
            target.material = DomumOrnamentumResolver.materialName(stack).orElse(null);
            target.variant = DomumOrnamentumResolver.variantName(stack, target.name).orElse(null);
            target.components = DomumOrnamentumResolver.componentsOf(stack);
            target.craftedIn = DO_WORKSTATION;
        }
        return target;
    }

    /** An {@link ItemCount} for a stack, optionally bound to an inventory slot. */
    public static ItemCount itemCount(ItemStack stack, int count, int slot) {
        ItemCount item = fillItem(new ItemCount(), stack);
        item.count = count;
        item.slot = slot;
        return item;
    }
}
