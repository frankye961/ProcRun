package dev.procrun.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;

/**
 * Constructor-injection exercise using explicit interface-to-implementation bindings.
 * Each resolution will create new instances; no scanning, field injection, or lifecycle management.
 */
@RequiredArgsConstructor
public class AutowireResolver {
    private final Map<Class<?>, Class<?>> bindings;

    /** Will resolve a dependency graph; invalid graphs and construction failures use IllegalStateException. */
    public <T> T resolve(Class<T> type) {
        return resolve(type, new HashSet<>());
    }

    /** Will resolve recursively, tracking the active construction path to detect cycles. */
    private <T> T resolve(Class<T> type, Set<Class<?>> resolving) {
        Class<?> clazz = implementationFor(type);
        if (!resolving.add(clazz)) {
            throw new IllegalStateException(
                    "Circular dependency involving " + clazz.getName());
        }
        try {
            var constructor = findConstructor(clazz);
            var arguments = resolveArguments(constructor, resolving);
            var instance = instantiate(constructor, arguments);
            return type.cast(instance);
        } finally {
            resolving.remove(clazz);
        }
    }

    /** Will select an explicit binding, or the requested concrete class; reject incompatible bindings. */
    private Class<?> implementationFor(Class<?> type) {
        var binding = bindings.get(type);
        var implementation = binding != null ? binding : type;
        if (binding != null) {
            if (!type.isAssignableFrom(binding)) {
                throw new IllegalStateException("Implementation " + binding.getName()
                        + " is not assignable to " + type.getName());
            }
        }

        if (implementation.isInterface()) {
            throw new IllegalStateException("Cannot resolve interface " + implementation.getName()
                    + " for requested type " + type.getName());
        }
        if (Modifier.isAbstract(implementation.getModifiers())) {
            throw new IllegalStateException("Cannot resolve abstract class " + implementation.getName()
                    + " for requested type " + type.getName());
        }
        return implementation;
    }

    /** Will select one public annotated constructor, or the sole public constructor if unannotated. */
    private Constructor<?> findConstructor(Class<?> implementation) {
        var publicConstructors = implementation.getConstructors();
        Set<Constructor<?>> annotatedConstructors = createAnnotatedConstructors(implementation);
        if (annotatedConstructors.size() == 1) return annotatedConstructors.iterator().next();
        else if (annotatedConstructors.size() == 0) {
            if (publicConstructors.length == 1) {
                return publicConstructors[0];
            }
            throw new IllegalStateException(
                    "Expected exactly one public constructor when none is annotated: "
                            + implementation.getName());
        } else {
            throw new IllegalStateException("Multiple constructors found");
        }
    }

    private static Set<Constructor<?>> createAnnotatedConstructors(Class<?> implementation) {
        Set<Constructor<?>> annotatedConstructors = new HashSet<>();
        Constructor<?>[] constructors = implementation.getConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.isAnnotationPresent(AutowireComponent.class)) {
                annotatedConstructors.add(constructor);
            }
        }
        return annotatedConstructors;
    }

    /** Will resolve constructor parameters in declaration order, sharing the active construction path. */
    private Object[] resolveArguments(Constructor<?> constructor, Set<Class<?>> resolving) {
        var params = constructor.getParameterTypes();
        Object[] arguments = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            arguments[i] = resolve(params[i], resolving);
        }
        return arguments;
    }

    /** Will invoke the constructor and wrap reflective failures while preserving the original cause. */
    private Object instantiate(Constructor<?> constructor, Object[] arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(
                    "Constructor failed for " + constructor.getDeclaringClass().getName(),
                    e.getCause());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot instantiate " + constructor.getDeclaringClass().getName(),
                    e);
        }
    }
}
