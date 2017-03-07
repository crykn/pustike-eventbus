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

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registry of subscribers to a single event bus.
 * @author Colin Decker
 */
final class SubscriberRegistry {
    /**
     * All registered subscribers, indexed by event type.
     *
     * <p>The {@link CopyOnWriteArraySet} values make it easy and relatively lightweight to get an immutable snapshot
     * of all current subscribers to an event without any locking.
     */
    private final ConcurrentMap<Integer, CopyOnWriteArraySet<Subscriber>> subscribers;

    /**
     * The event bus this registry belongs to.
     */
    private final EventBus bus;

    /**
     * The cache for subscriberMethods and eventTypeHierarchy.
     */
    private final SubscriberLoader subscriberLoader;

    SubscriberRegistry(EventBus bus) {
        this(bus, null);
    }

    SubscriberRegistry(EventBus bus, SubscriberLoader subscriberLoader) {
        this.bus = Objects.requireNonNull(bus);
        this.subscribers = new ConcurrentHashMap<>();
        this.subscriberLoader = subscriberLoader == null ? new DefaultSubscriberLoader() : subscriberLoader;
    }

    /**
     * Registers all subscriber methods on the given listener object.
     */
    void register(Object listener) {
        Map<Integer, List<Subscriber>> listenerMethods = findAllSubscribers(listener);
        for (Map.Entry<Integer, List<Subscriber>> entry : listenerMethods.entrySet()) {
            int hashCode = entry.getKey();
            Collection<Subscriber> eventMethodsInListener = entry.getValue();
            CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(hashCode);
            if (eventSubscribers == null) {
                CopyOnWriteArraySet<Subscriber> newSet = new CopyOnWriteArraySet<>();
                eventSubscribers = firstNonNull(subscribers.putIfAbsent(hashCode, newSet), newSet);
            }
            eventSubscribers.addAll(eventMethodsInListener);
        }
    }

    /**
     * Unregisters all subscribers on the given listener object.
     */
    void unregister(Object listener) {
        Map<Integer, List<Subscriber>> listenerMethods = findAllSubscribers(listener);
        for (Map.Entry<Integer, List<Subscriber>> entry : listenerMethods.entrySet()) {
            int hashCode = entry.getKey();
            Collection<Subscriber> listenerMethodsForType = entry.getValue();
            CopyOnWriteArraySet<Subscriber> currentSubscribers = subscribers.get(hashCode);
            if (currentSubscribers != null) {
                currentSubscribers.removeAll(listenerMethodsForType);
            }
            // don't try to remove the set if it's empty; that can't be done safely without a lock
            // anyway, if the set is empty it'll just be wrapping an array of length 0
        }
    }

    void unregister(Subscriber subscriber) {
        CopyOnWriteArraySet<Subscriber> currentSubscribers = subscribers.get(subscriber.registryKey);
        if (currentSubscribers != null) {
            currentSubscribers.remove(subscriber);
        }
    }

    /**
     * Gets an iterator representing an immutable snapshot of all subscribers to the given event at the time this method
     * is called.
     */
    Iterator<Subscriber> getSubscribers(Object event) {
        if (event instanceof TypeSupplier) {
            Class<?> eventSourceType = ((TypeSupplier) event).getType();
            int hashCode = (31 + event.getClass().getName().hashCode()) * 31 + eventSourceType.getName().hashCode();
            CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(hashCode);
            return eventSubscribers != null ? eventSubscribers.iterator() : Collections.emptyIterator();
        } else {
            Set<Class<?>> eventTypes = subscriberLoader.flattenHierarchy(event.getClass());
            LinkedList<Iterator<Subscriber>> subscriberIterators = new LinkedList<>();
            for (Class<?> eventType : eventTypes) {
                int hashCode = eventType.getName().hashCode();
                CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(hashCode);
                if (eventSubscribers != null) {// eager no-copy snapshot
                    subscriberIterators.add(eventSubscribers.iterator());
                }
            }
            return new IteratorAggregator<>(subscriberIterators);
        }
    }

    /**
     * Returns all subscribers for the given listener grouped by the type of event they subscribe to.
     */
    private Map<Integer, List<Subscriber>> findAllSubscribers(Object listener) {
        Map<Integer, List<Subscriber>> methodsInListener = new HashMap<>();
        WeakReference<?> weakListener = new WeakReference<>(listener);
        Class<?> clazz = listener.getClass();
        for (Method method : subscriberLoader.findSubscriberMethods(clazz)) {
            int hashCode = computeParameterHashCode(method);
            List<Subscriber> subscriberList = methodsInListener.get(hashCode);
            if (subscriberList == null) {
                subscriberList = new ArrayList<>();
                methodsInListener.put(hashCode, subscriberList);
            }
            subscriberList.add(Subscriber.create(bus, weakListener, method, hashCode));
        }
        return methodsInListener;
    }

    private int computeParameterHashCode(Method method) {
        Class<?> parameterClass = method.getParameterTypes()[0];
        Type parameterType = method.getGenericParameterTypes()[0];
        if (parameterClass.equals(TypedEvent.class) && parameterType instanceof ParameterizedType) {
            ParameterizedType firstParam = (ParameterizedType) parameterType;
            int hashCode = firstParam.getRawType().getTypeName().hashCode();
            Type[] typeArguments = firstParam.getActualTypeArguments();
            return (31 + hashCode) * 31 + typeArguments[0].getTypeName().hashCode();
        }
        return parameterClass.getName().hashCode();
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : Objects.requireNonNull(second);
    }

    /**
     * Clear all subscribers from the cache.
     */
    void clear() {
        subscribers.clear();
        subscriberLoader.invalidateAll();
    }

    Set<Subscriber> getSubscribersForTesting(Class<?> eventType) {
        int hashCode = eventType.getName().hashCode();
        /*if (event instanceof TypeSupplier) {
            Class<?> eventSourceType = ((TypeSupplier) event).getType();
            hashCode = (31 + hashCode) * 31 + eventSourceType.getName().hashCode();
        }*/
        CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(hashCode);
        return eventSubscribers != null ? eventSubscribers : Collections.emptySet();
    }

    private static final class IteratorAggregator<E> implements Iterator<E> {
        private LinkedList<Iterator<E>> internalIterators;
        private Iterator<E> currentIterator = null;

        private IteratorAggregator(List<Iterator<E>> iterators) {
            internalIterators = new LinkedList<>(iterators);
        }

        @Override
        public boolean hasNext() {
            return (currentIterator != null && currentIterator.hasNext()) ||
                    (!internalIterators.isEmpty() && internalIterators.getFirst().hasNext());
        }

        @Override
        public E next() {
            if (currentIterator != null && currentIterator.hasNext()) {
                return currentIterator.next();
            }
            if (!internalIterators.isEmpty()) {
                currentIterator = internalIterators.pollFirst();
                return currentIterator.next();
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
