/*
 * Copyright (C) 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.pustike.eventbus;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Cache for subscriberMethods that are annotated with {@code @Subscribe} in a given class and all it's super-classes.
 * And typeHierarchy cache with all super-classes and interfaces for a concrete class.
 */
public interface SubscriberLoader {
    /**
     * Find all methods in the given class and all it's super-classes, that are annotated with {@code @Subscribe}.
     * @param clazz the target listener class
     * @return a list of subscriber methods
     */
    List<Method> findSubscriberMethods(Class<?> clazz);

    /**
     * Find all super classes and interfaces for the given concrete class.
     * @param clazz the event class
     * @return a list of subscriber methods
     */
    Set<Class<?>> flattenHierarchy(Class<?> clazz);

    /**
     * Discards all entries in the cache.
     */
    void invalidateAll();
}
