package DahRealPanda.plugins.colonyweb.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small text helpers shared by the colony scanners: unwrapping Minecraft {@link Component}s
 * and turning raw registry ids / translation keys into readable names.
 *
 * <p>A dedicated server has no <em>modded</em> language files loaded, so a translatable
 * component from MineColonies resolves to its raw key (e.g.
 * {@code com.minecolonies.research.technology.stone.name}). {@link Translations} reads those
 * language files straight out of the mod jars so the real name can be used; when a key is
 * genuinely unknown, {@link #humanize(String)} makes it presentable anyway.</p>
 */
public final class Text {

    /**
     * Matches the {@code toString()} of a {@link TranslatableContents}, i.e.
     * {@code translation{key='some.key', args=[]}}.
     *
     * <p>Some MineColonies methods hand back the contents rather than the component wrapping
     * them, and that raw form used to leak into the dashboard verbatim.</p>
     */
    private static final Pattern CONTENTS_KEY = Pattern.compile("key='([^']+)'");

    private Text() {
    }

    /**
     * Convert a value to a display string, unwrapping Minecraft {@link Component}s and
     * resolving modded translation keys where possible.
     */
    public static String componentString(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Component c) {
            String translated = fromContents(c.getContents());
            // Only prefer our own lookup when the component is a bare translatable with no
            // siblings; anything richer is better rendered by Minecraft itself.
            if (translated != null && c.getSiblings().isEmpty()) {
                return translated;
            }
            return c.getString();
        }
        if (o instanceof ComponentContents contents) {
            String translated = fromContents(contents);
            if (translated != null) {
                return translated;
            }
        }
        String s = String.valueOf(o);
        Matcher matcher = CONTENTS_KEY.matcher(s);
        if (s.startsWith("translation{") && matcher.find()) {
            String key = matcher.group(1);
            String translated = Translations.lookup(key);
            return translated != null ? translated : key;
        }
        return s;
    }

    /** Resolve translatable contents through the mod language files, or null if we cannot. */
    private static String fromContents(ComponentContents contents) {
        if (!(contents instanceof TranslatableContents translatable)) {
            return null;
        }
        return Translations.format(translatable.getKey(), translatable.getArgs());
    }

    /** The part of a {@code namespace:path} id after the colon (or the input unchanged). */
    public static String pathOf(String registryName) {
        if (registryName == null) {
            return "";
        }
        int idx = registryName.indexOf(':');
        return idx >= 0 ? registryName.substring(idx + 1) : registryName;
    }

    /** Turn a raw id/path/key into a readable Title Case name (splits _, -, /, camelCase). */
    public static String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw;
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0 && slash < s.length() - 1) {
            s = s.substring(slash + 1);
        }
        s = s.replaceAll("\\.(blueprint|json)$", "");
        // Split camelCase / letter-digit boundaries and separators into spaces.
        s = s.replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Za-z])(?=[0-9])", " ")
                .replaceAll("(?<=[0-9])(?=[A-Za-z])", " ")
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ');
        String[] parts = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.length() > 1 ? p.substring(1) : "")
                    .append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Readable name for a value that may be a real display string or a raw translation key.
     * Keys like {@code com.minecolonies.research.technology.stone.name} become {@code "Stone"}.
     */
    public static String displayName(Object value, String fallback) {
        String s = componentString(value);
        if (s == null || s.isBlank()) {
            return fallback;
        }
        if (!looksLikeKey(s)) {
            return s;
        }
        // The key survived componentString, so no mod declares it — derive something readable.
        String key = s;
        // Trailing ".name"/".desc" segments carry no information — drop them.
        for (String suffix : new String[]{".name", ".description", ".desc", ".title"}) {
            if (key.endsWith(suffix)) {
                key = key.substring(0, key.length() - suffix.length());
                break;
            }
        }
        int dot = key.lastIndexOf('.');
        String tail = dot >= 0 && dot < key.length() - 1 ? key.substring(dot + 1) : key;
        String humanized = humanize(tail);
        return humanized.isBlank() ? fallback : humanized;
    }

    /** Null-safe string, for comparators that must not trip over missing names. */
    public static String stringOrEmpty(String s) {
        return s == null ? "" : s;
    }

    /** True when a string looks like a translation key / registry id rather than prose. */
    public static boolean looksLikeKey(String s) {
        return !s.contains(" ") && (s.indexOf('.') > 0 || s.indexOf(':') > 0);
    }
}
