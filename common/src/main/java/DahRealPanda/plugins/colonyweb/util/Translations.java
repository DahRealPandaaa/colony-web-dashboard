package DahRealPanda.plugins.colonyweb.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * English names for modded translation keys.
 *
 * <p>A dedicated server loads vanilla's {@code en_us.json} but nothing from mods, so anything
 * MineColonies names through a translatable component — research nodes, branches, effects, job
 * titles — arrives as a bare key like {@code com.minecolonies.research.civilian.name}. Mod jars
 * ship their own language files, so this reads them straight off the classpath once and keeps
 * the merged table in memory.</p>
 *
 * <p>Missing keys are not an error: {@link Text#displayName} falls back to humanizing the key.</p>
 */
public final class Translations {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new Gson();

    /** Null until the first lookup; never null afterwards, even if nothing loaded. */
    private static volatile Map<String, String> table;

    private Translations() {
    }

    /** The English string for a key, or null when no mod declares it. */
    public static String lookup(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return table().get(key);
    }

    /**
     * Resolve a key and substitute its arguments, the way the client would.
     *
     * <p>MineColonies research effects are patterns like {@code "Increases %s by %s"}, so the
     * arguments matter — without them the text reads as nonsense.</p>
     */
    public static String format(String key, Object[] args) {
        String pattern = lookup(key);
        if (pattern == null) {
            return null;
        }
        return args == null || args.length == 0 ? pattern : substitute(pattern, args);
    }

    /**
     * Replace {@code %s} / {@code %d} in order and {@code %N$s} positionally.
     *
     * <p>Hand-rolled rather than {@link String#format} because a stray {@code %} in a mod's
     * language file would otherwise throw, and a broken label is not worth an exception.</p>
     */
    private static String substitute(String pattern, Object[] args) {
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        int next = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != '%' || i == pattern.length() - 1) {
                out.append(c);
                continue;
            }
            // "%%" is a literal percent sign.
            char after = pattern.charAt(i + 1);
            if (after == '%') {
                out.append('%');
                i++;
                continue;
            }
            // Optional "N$" index prefix.
            int j = i + 1;
            int index = -1;
            int digits = j;
            while (digits < pattern.length() && Character.isDigit(pattern.charAt(digits))) {
                digits++;
            }
            if (digits > j && digits < pattern.length() && pattern.charAt(digits) == '$') {
                index = Integer.parseInt(pattern.substring(j, digits)) - 1;
                j = digits + 1;
            }
            if (j >= pattern.length() || "sdf".indexOf(pattern.charAt(j)) < 0) {
                out.append(c);
                continue;
            }
            int use = index >= 0 ? index : next++;
            out.append(use >= 0 && use < args.length ? argString(args[use]) : "");
            i = j;
        }
        return out.toString();
    }

    /** Arguments are often components themselves, so resolve them the same way. */
    private static String argString(Object arg) {
        String s = Text.componentString(arg);
        return s == null ? "" : s;
    }

    private static Map<String, String> table() {
        Map<String, String> loaded = table;
        if (loaded == null) {
            synchronized (Translations.class) {
                loaded = table;
                if (loaded == null) {
                    loaded = load();
                    table = loaded;
                }
            }
        }
        return loaded;
    }

    /** Merge {@code assets/<modid>/lang/en_us.json} for every loaded mod. */
    private static Map<String, String> load() {
        Map<String, String> merged = new HashMap<>();
        for (String modId : modIds()) {
            byte[] bytes = classpathBytes("assets/" + modId + "/lang/en_us.json");
            if (bytes == null) {
                continue;
            }
            try {
                JsonObject json = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
                if (json == null) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && value.isJsonPrimitive()) {
                        merged.put(entry.getKey(), value.getAsString());
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[ColonyWeb] could not read the language file for {}", modId, e);
            }
        }
        LOGGER.info("[ColonyWeb] loaded {} modded translations", merged.size());
        return merged;
    }

    private static Iterable<String> modIds() {
        try {
            return ModList.get().getMods().stream().map(mod -> mod.getModId()).toList();
        } catch (Throwable t) {
            // Called before the mod list exists (or outside Forge entirely) — the two that
            // matter are worth trying anyway.
            return java.util.List.of("minecolonies", "domum_ornamentum");
        }
    }

    private static byte[] classpathBytes(String resource) {
        try (InputStream in = Translations.class.getClassLoader().getResourceAsStream(resource)) {
            return in != null ? in.readAllBytes() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
