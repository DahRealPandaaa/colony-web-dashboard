package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import DahRealPanda.plugins.colonyweb.colony.model.ColonySnapshot;
import DahRealPanda.plugins.colonyweb.colony.model.ItemInfo;
import DahRealPanda.plugins.colonyweb.texture.DomumOrnamentumResolver;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static DahRealPanda.plugins.colonyweb.colony.MineColoniesReflect.invokeAny;

/**
 * Works out which items the colony can actually make.
 *
 * <p>Every crafting building keeps a list of recipe tokens its workers have learned; the colony
 * manager resolves those tokens to recipes. Collecting each recipe's output tells the dashboard
 * which items are one work order away rather than a trip to the mine — which is the difference
 * between "we're blocked" and "just queue it".</p>
 */
public final class RecipeScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String COLONY_MANAGER = "com.minecolonies.api.colony.IColonyManager";

    /** Texture keys of everything the colony knows how to craft. */
    public Set<String> scan(Collection<Object> buildings) {
        Set<String> craftable = new HashSet<>();
        Object recipeManager = recipeManager();
        if (recipeManager == null) {
            return craftable;
        }
        for (Object building : buildings) {
            try {
                for (Object token : tokensOf(building)) {
                    ItemStack output = outputOf(recipeManager, token);
                    if (output != null && !output.isEmpty()) {
                        craftable.add(DomumOrnamentumResolver.textureKeyFor(output));
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("{} failed to read recipes for a building", ColonyWeb.LOG, t);
            }
        }
        return craftable;
    }

    private Object recipeManager() {
        Object manager = MineColoniesReflect.invokeStatic(COLONY_MANAGER, "getInstance").orElse(null);
        return invokeAny(manager, "getRecipeManager").orElse(null);
    }

    /**
     * Recipe tokens a building knows.
     *
     * <p>Recent MineColonies moved crafting onto building modules, so the flat call is tried
     * first and the module list second.</p>
     */
    private Collection<Object> tokensOf(Object building) {
        Object direct = invokeAny(building, "getRecipes").orElse(null);
        if (direct instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }
        Set<Object> tokens = new HashSet<>();
        Object modules = invokeAny(building, "getModules").orElse(null);
        if (modules instanceof Collection<?> collection) {
            for (Object module : collection) {
                Object fromModule = Scan.firstNonNull(
                        invokeAny(module, "getRecipes").orElse(null),
                        invokeAny(module, "getRecipeTokens").orElse(null));
                if (fromModule instanceof Collection<?> list) {
                    tokens.addAll(list);
                }
            }
        }
        return tokens;
    }

    private ItemStack outputOf(Object recipeManager, Object token) {
        Object byToken = invokeAny(recipeManager, "getRecipe", token).orElse(null);
        if (byToken == null) {
            Object all = invokeAny(recipeManager, "getRecipes").orElse(null);
            if (all instanceof Map<?, ?> map) {
                byToken = map.get(token);
            }
        }
        return Scan.itemStackOf(Scan.firstNonNull(
                invokeAny(byToken, "getPrimaryOutput").orElse(null),
                invokeAny(byToken, "getResultItem").orElse(null)));
    }

    /**
     * Flag every item in a finished scan that the colony can craft.
     *
     * <p>Done as a pass over the built payloads rather than inside {@code Scan.fillItem}, because
     * the recipe list is a colony-wide fact that is not known while individual stacks are read.</p>
     */
    public static void markCraftable(ColonyScan scan, Set<String> craftable) {
        if (craftable.isEmpty()) {
            return;
        }
        ColonySnapshot snapshot = scan.snapshot;
        mark(snapshot.warehouse.stacks, craftable);
        snapshot.buildings.forEach(building -> mark(building.required, craftable));
        scan.inventories.values().forEach(items -> mark(items, craftable));
        if (scan.research != null) {
            scan.research.branches.forEach(branch ->
                    branch.researches.forEach(entry -> mark(entry.cost, craftable)));
        }
    }

    private static void mark(Collection<? extends ItemInfo> items, Set<String> craftable) {
        for (ItemInfo item : items) {
            if (item.itemKey != null && craftable.contains(item.itemKey)) {
                item.craftable = true;
            }
        }
    }
}
