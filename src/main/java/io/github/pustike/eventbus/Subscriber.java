/*
 * Copyright (C) 2016 the original author or authors.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * A subscriber method on a specific object, plus the executor that should be used for dispatching events to it.
 *
 * <p>Two subscribers are equivalent when they refer to the same method on the same object (not class). This property is
 * used to ensure that no subscriber method is registered more than once.
 * @author Colin Decker
 */
class Subscriber {
    /**
     * Creates a {@code Subscriber} for {@code method} on {@code listener}.
     */
    static Subscriber create(EventBus bus, Object listener, Method method, int registryKey) {
        Subscribe subscribe = method.getAnnotation(Subscribe.class);
        return subscribe != null && subscribe.threadSafe() ? new Subscriber(bus, listener, method, registryKey)
                : new SynchronizedSubscriber(bus, listener, method, registryKey);
    }

    /** The event bus this subscriber belongs to. */
    private final EventBus eventBus;

    /** The object with the subscriber method. */
    private final Object listener;

    /** Subscriber method. */
    private final Method method;

    /** The pre-computed hash code. */
    private final int hashCode;

    /** The parameter hashCode of this subscriber used by the registry. */
    final int registryKey;

    private Subscriber(EventBus eventBus, Object listener, Method method, int registryKey) {
        this.eventBus = Objects.requireNonNull(eventBus);
        this.listener = Objects.requireNonNull(listener);
        this.method = method;
        method.setAccessible(true);
        this.registryKey = registryKey;
        this.hashCode = computeHashCode(listener);
    }

    /**
     * Dispatches {@code event} to this subscriber using the proper executor.
     */
    final void dispatchEvent(final Object event) {
        eventBus.executor().execute(() -> {
            try {
                invokeSubscriberMethod(event);
            } catch (InvocationTargetException e) {
                // do not publish if an exception occurs in the exception event handler
                if (!(event instanceof ExceptionEvent)) {
                    eventBus.publish(new ExceptionEvent(eventBus, getTarget(), method, event, e.getCause()));
                }
            }
        });
    }

    /**
     * Invokes the subscriber method. This method can be overridden to make the invocation synchronized.
     */
    void invokeSubscriberMethod(Object event) throws InvocationTargetException {
        try {
            Object target = getTarget();
            if (target == null) {// if the target object is no longer available,
                eventBus.unsubscribe(this); // un-subscribe this subscriber!
                return;
            }
            method.invoke(target, event);
        } catch (IllegalArgumentException e) {
            throw new Error("Method rejected target/argument: " + event, e);
        } catch (IllegalAccessException e) {
            throw new Error("Method became inaccessible: " + event, e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }

    Object getTarget() {
        return listener instanceof WeakReference<?> ? ((WeakReference<?>) listener).get() : listener;
    }

    private int computeHashCode(Object listener) {
        return (31 + method.hashCode()) * 31 + System.identityHashCode(getTarget());
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof Subscriber) {
            Subscriber that = (Subscriber) obj;
            // Use == so that different equal instances will still receive events.
            // We only guard against the case that the same object is registered multiple times
            return getTarget() == that.getTarget() && method.equals(that.method);
        }
        return false;
    }

    /**
     * Subscriber that synchronizes invocations of a method to ensure that only one thread may enter the method at a
     * time.
     */
    static final class SynchronizedSubscriber extends Subscriber {
        private SynchronizedSubscriber(EventBus bus, Object target, Method method, int registryKey) {
            super(bus, target, method, registryKey);
        }

        @Override
        void invokeSubscriberMethod(Object event) throws InvocationTargetException {
            synchronized (this) {
                super.invokeSubscriberMethod(event);
            }
        }
    }
}
