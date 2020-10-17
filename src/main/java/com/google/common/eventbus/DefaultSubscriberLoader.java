/*
 * Copyright (C) 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.eventbus;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default Cache that uses {@link ConcurrentHashMap} to store subscribeMethods and eventType hierarchy.
 */
public class DefaultSubscriberLoader implements SubscriberLoader {
    /** The internal cache to store subscribeMethods, when no external cache is provided. */
    private final Map<Class<?>, Iterable<Method>> subscribeMethodsCache;
    /** The internal cache to store eventType hierarchy, when no external loader is provided. */
    private final Map<Class<?>, Set<Class<?>>> typeHierarchyCache;

    public DefaultSubscriberLoader() {
        this.subscribeMethodsCache = new ConcurrentHashMap<>();
        this.typeHierarchyCache = new ConcurrentHashMap<>();
    }

    @Override
    public Iterable<Method> findSubscriberMethods(Class<?> clazz) {
        return subscribeMethodsCache.computeIfAbsent(clazz, this::findAnnotatedMethodsNotCached);
    }

    @Override
    public Set<Class<?>> flattenHierarchy(Class<?> clazz) {
        return typeHierarchyCache.computeIfAbsent(clazz, this::flattenHierarchyNotCached);
    }

    @Override
    public void invalidateAll() {
        subscribeMethodsCache.clear();
        typeHierarchyCache.clear();
    }

    /**
     * Find all methods in the given class and all its super-classes, that are annotated with {@code @Subscribe}.
     * @param clazz the target listener class
     * @return a list of subscriber methods
     */
    protected final Iterable<Method> findAnnotatedMethodsNotCached(Class<?> clazz) {
        Map<Integer, Method> subscribeMethods = new HashMap<>();
        Set<Class<?>> allSuperTypes = flattenHierarchy(clazz);
        for (Class<?> superType : allSuperTypes) {
            for (Method method : superType.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Subscribe.class) && !method.isSynthetic()) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length != 1) {
                        String message = "Method %s has @Subscribe annotation but has %d parameters."
                                + " EventHandler methods must have exactly 1 parameter.";
                        throw new IllegalArgumentException(String.format(message, method, parameterTypes.length));
                    }
                    int hashCode = Objects.hash(method.getName(), method.getParameterTypes());
                    if (!subscribeMethods.containsKey(hashCode)) {
                        subscribeMethods.put(hashCode, method);
                    }
                }
            }
        }
        return Collections.unmodifiableCollection(subscribeMethods.values());
    }

    /**
     * Find all super classes and interfaces for the given concrete class.
     * @param concreteClass the event class
     * @return a list of subscriber methods
     */
    protected final Set<Class<?>> flattenHierarchyNotCached(Class<?> concreteClass) {
        Set<Class<?>> allSuperTypes = new LinkedHashSet<>();
        while (concreteClass != null) {
            allSuperTypes.add(concreteClass);
            for (Class<?> interfaceType : concreteClass.getInterfaces()) {
                allSuperTypes.addAll(flattenHierarchyNotCached(interfaceType));
            }
            concreteClass = concreteClass.getSuperclass();
        }
        return Collections.unmodifiableSet(allSuperTypes);
    }
}
