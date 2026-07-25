package DahRealPanda.plugins.untitled1.colony;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central, fail-soft reflection layer for MineColonies (and related) runtime access.
 *
 * <p>The mod compiles and loads without MineColonies present. Every lookup here returns an
 * {@link Optional} (or empty result) so a missing/renamed member never crashes the mod.
 * Resolved {@link Class}/{@link Method}/{@link Field} handles are cached.</p>
 */
public final class MineColoniesReflect {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ConcurrentHashMap<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private static Boolean loaded;

    private MineColoniesReflect() {
    }

    /** @return true when MineColonies is present in the runtime. */
    public static boolean isMineColoniesLoaded() {
        if (loaded == null) {
            boolean present;
            try {
                present = ModList.get() != null && ModList.get().isLoaded("minecolonies");
            } catch (Throwable t) {
                present = false;
            }
            loaded = present;
            LOGGER.info("[ColonyWeb] MineColonies detected: {}", present);
        }
        return loaded;
    }

    /** Resolve a class by fully-qualified name, cached. */
    public static Optional<Class<?>> resolve(String fqcn) {
        return CLASS_CACHE.computeIfAbsent(fqcn, name -> {
            try {
                return Optional.of(Class.forName(name, false, MineColoniesReflect.class.getClassLoader()));
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] class not found: {}", name);
                return Optional.empty();
            }
        });
    }

    /** Resolve a no-arg (or given parameter-type) method on a class, cached. */
    public static Optional<Method> method(Class<?> owner, String name, Class<?>... params) {
        if (owner == null) {
            return Optional.empty();
        }
        String key = owner.getName() + "#" + name + "(" + params.length + ")";
        return METHOD_CACHE.computeIfAbsent(key, k -> {
            try {
                Method m = owner.getMethod(name, params);
                m.setAccessible(true);
                return Optional.of(m);
            } catch (Throwable ignored) {
                // fall back to declared methods across the hierarchy
                Class<?> c = owner;
                while (c != null) {
                    try {
                        Method m = c.getDeclaredMethod(name, params);
                        m.setAccessible(true);
                        return Optional.of(m);
                    } catch (Throwable ignore) {
                        c = c.getSuperclass();
                    }
                }
                LOGGER.debug("[ColonyWeb] method not found: {}.{}", owner.getName(), name);
                return Optional.empty();
            }
        });
    }

    /** Resolve a field on a class (searches hierarchy), cached. */
    public static Optional<Field> field(Class<?> owner, String name) {
        if (owner == null) {
            return Optional.empty();
        }
        String key = owner.getName() + "@" + name;
        return FIELD_CACHE.computeIfAbsent(key, k -> {
            Class<?> c = owner;
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return Optional.of(f);
                } catch (Throwable ignore) {
                    c = c.getSuperclass();
                }
            }
            LOGGER.debug("[ColonyWeb] field not found: {}.{}", owner.getName(), name);
            return Optional.empty();
        });
    }

    /** Invoke a named no-arg method on an object, fail-soft. */
    public static Optional<Object> invoke(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        return method(target.getClass(), methodName).flatMap(m -> {
            try {
                return Optional.ofNullable(m.invoke(target));
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] invoke failed: {}.{}", target.getClass().getName(), methodName);
                return Optional.empty();
            }
        });
    }

    /** Invoke a named method with arguments, fail-soft. */
    public static Optional<Object> invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        if (target == null) {
            return Optional.empty();
        }
        return method(target.getClass(), methodName, paramTypes).flatMap(m -> {
            try {
                return Optional.ofNullable(m.invoke(target, args));
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] invoke(args) failed: {}.{}", target.getClass().getName(), methodName);
                return Optional.empty();
            }
        });
    }

    /** Invoke a static no-arg method on a resolved class, fail-soft. */
    public static Optional<Object> invokeStatic(String fqcn, String methodName) {
        return resolve(fqcn).flatMap(cls -> method(cls, methodName).flatMap(m -> {
            try {
                return Optional.ofNullable(m.invoke(null));
            } catch (Throwable t) {
                LOGGER.debug("[ColonyWeb] invokeStatic failed: {}.{}", fqcn, methodName);
                return Optional.empty();
            }
        }));
    }

    /** Read a field value from an object, fail-soft. */
    public static Optional<Object> fieldValue(Object target, String fieldName) {
        if (target == null) {
            return Optional.empty();
        }
        return field(target.getClass(), fieldName).flatMap(f -> {
            try {
                return Optional.ofNullable(f.get(target));
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
    }
}
