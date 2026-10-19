package dev.procrun.config;

import java.lang.reflect.Constructor;
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
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Will resolve recursively, tracking the active construction path to detect cycles. */
    private <T> T resolve(Class<T> type, Set<Class<?>> resolving) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Will select an explicit binding, or the requested concrete class; reject incompatible bindings. */
    private Class<?> implementationFor(Class<?> type) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Will select one public annotated constructor, or the sole public constructor if unannotated. */
    private Constructor<?> findConstructor(Class<?> implementation) {
        var publicConstructors = implementation.getConstructors();
        Set<Constructor<?>> annotatedConstructors = createAnnotatedConstructors(implementation);
        if(annotatedConstructors.size() == 1) return annotatedConstructors.iterator().next();
        else if (annotatedConstructors.size() == 0) {
           if (publicConstructors.length == 1){
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
        for(Constructor<?> constructor : constructors){
            if(constructor.isAnnotationPresent(AutowireComponent.class)){
                annotatedConstructors.add(constructor);
            }
        }
        return annotatedConstructors;
    }

    /** Will resolve constructor parameters in declaration order, sharing the active construction path. */
    private Object[] resolveArguments(Constructor<?> constructor, Set<Class<?>> resolving) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Will invoke the constructor and wrap reflective failures while preserving the original cause. */
    private Object instantiate(Constructor<?> constructor, Object[] arguments) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
