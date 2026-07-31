package DahRealPanda.plugins.colonyweb.util
import java.util.Optional

import DahRealPanda.plugins.colonyweb.platform.Platform
import com.mojang.logging.LogUtils
import org.slf4j.Logger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

/**
 * Central, fail-soft reflection layer for MineColonies (and related) runtime access.
 * The mod compiles and loads without MineColonies present. Every lookup returns an
 * Optional (or empty result) so a missing/renamed member never crashes the mod.
 */
object MineColoniesReflect {
    private val LOGGER: Logger = LogUtils.getLogger()

    private val CLASS_CACHE = ConcurrentHashMap<String, Optional<Class<*>>>()
    private val METHOD_CACHE = ConcurrentHashMap<String, Optional<Method>>()
    private val FIELD_CACHE = ConcurrentHashMap<String, Optional<Field>>()
    private val METHODS_CACHE = ConcurrentHashMap<Class<*>, Array<Method>>()
    private val LOGGED_MISSES = ConcurrentHashMap.newKeySet<String>()

    private var loaded: Boolean? = null

    fun isMineColoniesLoaded(): Boolean {
        if (loaded == null) {
            val present: Boolean = try {
                Platform.get().isModLoaded("minecolonies")
            } catch (_: Throwable) {
                false
            }
            loaded = present
            LOGGER.info("[ColonyWeb] MineColonies detected: {}", present)
        }
        return loaded ?: false.also { loaded = false }
    }

    fun resolve(fqcn: String): Optional<Class<*>> {
        return CLASS_CACHE.computeIfAbsent(fqcn) { name ->
            try {
                Optional.of(Class.forName(name, false, MineColoniesReflect::class.java.classLoader))
            } catch (_: Throwable) {
                LOGGER.debug("[ColonyWeb] class not found: {}", name)
                Optional.empty()
            }
        }
    }

    fun method(owner: Class<*>?, name: String, vararg params: Class<*>): Optional<Method> {
        if (owner == null) {
            return Optional.empty()
        }
        val key = owner.name + "#" + name + "(" + params.size + ")"
        return METHOD_CACHE.computeIfAbsent(key) { _ ->
            try {
                val m = owner.getMethod(name, *params)
                m.isAccessible = true
                Optional.of(m)
            } catch (_: Throwable) {
                var c: Class<*>? = owner
                while (c != null) {
                    try {
                        val m = c.getDeclaredMethod(name, *params)
                        m.isAccessible = true
                        return@computeIfAbsent Optional.of(m)
                    } catch (_: Throwable) {
                        c = c.superclass
                    }
                }
                LOGGER.debug("[ColonyWeb] method not found: {}.{}", owner.name, name)
                Optional.empty()
            }
        }
    }

    fun field(owner: Class<*>?, name: String): Optional<Field> {
        if (owner == null) {
            return Optional.empty()
        }
        val key = owner.name + "@" + name
        return FIELD_CACHE.computeIfAbsent(key) { _ ->
            var c: Class<*>? = owner
            while (c != null) {
                try {
                    val f = c.getDeclaredField(name)
                    f.isAccessible = true
                    return@computeIfAbsent Optional.of(f)
                } catch (_: Throwable) {
                    c = c.superclass
                }
            }
            LOGGER.debug("[ColonyWeb] field not found: {}.{}", owner.name, name)
            Optional.empty()
        }
    }

    fun invoke(target: Any?, methodName: String): Optional<Any> {
        if (target == null) {
            return Optional.empty()
        }
        return method(target.javaClass, methodName).flatMap { m ->
            try {
                Optional.ofNullable(m.invoke(target))
            } catch (_: Throwable) {
                LOGGER.debug("[ColonyWeb] invoke failed: {}.{}", target.javaClass.name, methodName)
                Optional.empty()
            }
        }
    }

    fun invoke(target: Any?, methodName: String, paramTypes: Array<Class<*>>, vararg args: Any?): Optional<Any> {
        if (target == null) {
            return Optional.empty()
        }
        return method(target.javaClass, methodName, *paramTypes).flatMap { m ->
            try {
                Optional.ofNullable(m.invoke(target, *args))
            } catch (_: Throwable) {
                LOGGER.debug("[ColonyWeb] invoke(args) failed: {}.{}", target.javaClass.name, methodName)
                Optional.empty()
            }
        }
    }

    fun invokeStatic(fqcn: String, methodName: String): Optional<Any> {
        return resolve(fqcn).flatMap { cls ->
            method(cls, methodName).flatMap { m ->
                try {
                    Optional.ofNullable(m.invoke(null))
                } catch (_: Throwable) {
                    LOGGER.debug("[ColonyWeb] invokeStatic failed: {}.{}", fqcn, methodName)
                    Optional.empty()
                }
            }
        }
    }

    fun invokeAny(target: Any?, name: String, vararg args: Any?): Optional<Any> {
        return invokeMatching(target, name, true, *args)
    }

    fun invokeAnyOf(target: Any?, vararg names: String): Optional<Any> {
        if (target == null) {
            return Optional.empty()
        }
        for (name in names) {
            val result = invokeMatching(target, name, false)
            if (result.isPresent) {
                return result
            }
        }
        logMissOnce(target.javaClass, names.joinToString("/"))
        return Optional.empty()
    }

    private fun invokeMatching(target: Any?, name: String, logMiss: Boolean, vararg args: Any?): Optional<Any> {
        if (target == null) {
            return Optional.empty()
        }
        val methods = methodsOf(target.javaClass)
        for (arity in args.size downTo 0) {
            val callArgs: Array<Any?> = if (arity == args.size) args as Array<Any?> else Arrays.copyOf(args, arity)
            for (m in methods) {
                if (m.parameterCount != arity || m.name != name) {
                    continue
                }
                try {
                    m.isAccessible = true
                    return Optional.ofNullable(m.invoke(target, *callArgs))
                } catch (_: Throwable) {
                }
            }
        }
        if (logMiss) {
            logMissOnce(target.javaClass, name)
        }
        return Optional.empty()
    }

    /**
     * Report a missing member once per class+name. The scanners call reflection once per
     * citizen/research/work order, so an unconditional log here buries the rest of the
     * server log under thousands of identical lines.
     */
    private fun logMissOnce(owner: Class<*>, name: String) {
        if (LOGGED_MISSES.add(owner.name + "#" + name)) {
            LOGGER.debug("[ColonyWeb] invokeAny found no usable {}.{}", owner.name, name)
        }
    }

    fun staticFieldValue(fqcn: String, fieldName: String): Optional<Any> {
        return resolve(fqcn).flatMap { cls ->
            field(cls, fieldName).flatMap { f ->
                try {
                    Optional.ofNullable(f.get(null))
                } catch (_: Throwable) {
                    Optional.empty()
                }
            }
        }
    }

    private fun methodsOf(owner: Class<*>): Array<Method> {
        return METHODS_CACHE.computeIfAbsent(owner) { cls ->
            val byKey = linkedMapOf<String, Method>()
            for (m in cls.methods) {
                byKey.putIfAbsent(signature(m), m)
            }
            var c: Class<*>? = cls
            while (c != null) {
                for (m in c.declaredMethods) {
                    byKey.putIfAbsent(signature(m), m)
                }
                c = c.superclass
            }
            byKey.values.toTypedArray()
        }
    }

    private fun signature(m: Method): String {
        return m.name + Arrays.toString(m.parameterTypes)
    }

    fun fieldValue(target: Any?, fieldName: String): Optional<Any> {
        if (target == null) {
            return Optional.empty()
        }
        return field(target.javaClass, fieldName).flatMap { f ->
            try {
                Optional.ofNullable(f.get(target))
            } catch (_: Throwable) {
                Optional.empty()
            }
        }
    }
}
