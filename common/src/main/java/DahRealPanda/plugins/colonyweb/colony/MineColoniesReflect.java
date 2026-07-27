package DahRealPanda.plugins.colonyweb.colony;

import DahRealPanda.plugins.colonyweb.platform.Platform;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
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
    private static final ConcurrentHashMap<Class<?>, Method[]> METHODS_CACHE = new ConcurrentHashMap<>();

    private static Boolean loaded;

    private MineColoniesReflect() {
    }

    /** @return true when MineColonies is present in the runtime. */
    public static boolean isMineColoniesLoaded() {
        if (loaded == null) {
            boolean present;
            try {
                present = Platform.get().isModLoaded("minecolonies");
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

    /**
     * Invoke a method by name without knowing its exact signature, fail-soft.
     *
     * <p>MineColonies moves parameters around between versions (e.g. {@code getHappiness()}
     * vs {@code getHappiness(colony, citizen)}). This tries the highest-arity overload that
     * matches the supplied arguments first, then progressively shorter prefixes of them, so a
     * single call site works across signatures.</p>
     */
    public static Optional<Object> invokeAny(Object target, String name, Object... args) {
        if (target == null) {
            return Optional.empty();
        }
        Method[] methods = methodsOf(target.getClass());
        for (int arity = args.length; arity >= 0; arity--) {
            Object[] callArgs = arity == args.length ? args : Arrays.copyOf(args, arity);
            for (Method m : methods) {
                if (m.getParameterCount() != arity || !m.getName().equals(name)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    return Optional.ofNullable(m.invoke(target, callArgs));
                } catch (Throwable ignored) {
                    // Wrong overload or the call threw — keep looking.
                }
            }
        }
        LOGGER.debug("[ColonyWeb] invokeAny found no usable {}.{}", target.getClass().getName(), name);
        return Optional.empty();
    }

    /** Read a static field from a resolved class, fail-soft. */
    public static Optional<Object> staticFieldValue(String fqcn, String fieldName) {
        return resolve(fqcn).flatMap(cls -> field(cls, fieldName)).flatMap(f -> {
            try {
                return Optional.ofNullable(f.get(null));
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
    }

    /** All public + declared methods of a class hierarchy, cached. */
    private static Method[] methodsOf(Class<?> owner) {
        return METHODS_CACHE.computeIfAbsent(owner, cls -> {
            // Keyed by the full signature so same-arity overloads are all kept — invokeAny
            // relies on being able to try each of them in turn.
            java.util.LinkedHashMap<String, Method> byKey = new java.util.LinkedHashMap<>();
            for (Method m : cls.getMethods()) {
                byKey.putIfAbsent(signature(m), m);
            }
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    byKey.putIfAbsent(signature(m), m);
                }
            }
            return byKey.values().toArray(new Method[0]);
        });
    }

    private static String signature(Method m) {
        return m.getName() + Arrays.toString(m.getParameterTypes());
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
