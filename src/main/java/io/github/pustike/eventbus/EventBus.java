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
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Dispatches events to listeners, and provides ways for listeners to register themselves.
 *
 * <p>The EventBus allows publish-subscribe-style communication between components without requiring the components to
 * explicitly register with one another (and thus be aware of each other).  It is designed exclusively to replace
 * traditional Java in-process event distribution using explicit registration. It is <em>not</em> a general-purpose
 * publish-subscribe system, nor is it intended for interprocess communication.
 *
 * <h2>Receiving Events</h2> <p>To receive events, an object should: <ol> <li>Expose a public method, known as the
 * <i>event subscriber</i>, which accepts a single argument of the type of event desired;</li> <li>Mark it with a {@link
 * Subscribe} annotation;</li> <li>Pass itself to an EventBus instance's {@link #register(Object)} method. </li> </ol>
 *
 * <h2>Posting Events</h2> <p>To post an event, simply provide the event object to the {@link #publish(Object)} method.
 * The EventBus instance will determine the type of event and route it to all registered listeners.
 *
 * <p>Events are routed based on their type &mdash; an event will be delivered to any subscriber for any type to which
 * the event is <em>assignable.</em>  This includes implemented interfaces, all superclasses, and all interfaces
 * implemented by superclasses.
 *
 * <p>When {@code post} is called, all registered subscribers for an event are run in sequence, so subscribers should be
 * reasonably quick.  If an event may trigger an extended process (such as a database load), spawn a thread or queue it
 * for later.
 *
 * <h2>Subscriber Methods</h2> <p>Event subscriber methods must accept only one argument: the event.
 *
 * <p>Subscribers should not, in general, throw.  If they do, the EventBus will catch and log the exception.  This is
 * rarely the right solution for error handling and should not be relied upon; it is intended solely to help find
 * problems during development.
 *
 * <p>The EventBus guarantees that it will not call a subscriber method from multiple threads simultaneously. Subscriber
 * methods need not worry about being reentrant, unless also called from outside the EventBus.
 *
 * <h2>Dead Events</h2> <p>If an event is posted, but no registered subscribers can accept it, it is considered "dead."
 * To give the system a second chance to handle dead events, they are wrapped in an instance of {@link DeadEvent} and
 * reposted.
 *
 * <p>If a subscriber for a supertype of all events (such as Object) is registered, no event will ever be considered
 * dead, and no DeadEvents will be generated. Accordingly, while DeadEvent extends {@link Object}, a subscriber
 * registered to receive any Object will never receive a DeadEvent.
 *
 * <p>This class is safe for concurrent use.
 *
 * <p>See the Guava User Guide article on <a href= "https://github.com/google/guava/wiki/EventBusExplained"> {@code
 * EventBus}</a>.
 * @author Cliff Biffle
 */
public final class EventBus {
    // the identifier for this event bus
    private final String identifier;
    // executor to dispatch events received from the dispatcher
    private final Executor executor;
    // handler for dispatching events to subscribers
    private final Dispatcher dispatcher;
    private final SubscriberRegistry subscriberRegistry;

    /**
     * Creates a new EventBus named "default".
     */
    public EventBus() {
        this("default");
    }

    /**
     * Creates a new EventBus with the given {@code identifier}.
     * @param identifier a brief name for this bus, for logging purposes.
     */
    public EventBus(String identifier) {
        this(identifier, Dispatcher.perThreadDispatchQueue(), createDirectExecutor(), null, null);
    }

    /**
     * Creates a new EventBus with the given {@code executor} and {@code dispatcher}.
     * @param dispatcher handler for dispatching events to subscribers
     * @param executor to dispatch events received from the dispatcher.
     */
    public EventBus(Dispatcher dispatcher, Executor executor) {
        this("default", dispatcher, executor, null, null);
    }

    /**
     * Creates a new EventBus using external cache for subscriberMethods and eventSuperTypes.
     *
     * <p>If subscriberMethodsLoader or typeHierarchyLoader is null, an internal cache is created to store them. To use
     * an external cache the following approach can be used. For ex. using Caffeine cache:
     * <pre>{@code
     * LoadingCache<Class<?>, List<Method>> subscriberMethodCache = Caffeine.newBuilder()
     *      .weakKeys().build(SubscriberRegistry::getAnnotatedMethodsNotCached);
     * LoadingCache<Class<?>, Set<Class<?>>> typeHierarchyCache = Caffeine.newBuilder()
     *      .weakKeys().weakValues().build(SubscriberRegistry::flattenHierarchyNotCached);
     * final EventBus eventBus = new EventBus("default", Dispatcher.perThreadDispatchQueue(), Runnable::run,
     *      subscriberMethodCache::get, typeHierarchyCache::get);
     * }</pre>
     * @param identifier a brief name for this bus, for logging purposes.
     * @param dispatcher handler for dispatching events to subscribers
     * @param executor to dispatch events received from the dispatcher.
     * @param subscriberMethodsLoader the function to find subscriberMethods from cache
     */
    public EventBus(String identifier, Dispatcher dispatcher, Executor executor,
                    Function<Class<?>, List<Method>> subscriberMethodsLoader,
                    Function<Class<?>, Set<Class<?>>> typeHierarchyLoader) {
        this.identifier = Objects.requireNonNull(identifier);
        this.executor = Objects.requireNonNull(executor);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.subscriberRegistry = new SubscriberRegistry(this, subscriberMethodsLoader, typeHierarchyLoader);
    }

    /**
     * Returns the identifier for this event bus.
     */
    public final String getIdentifier() {
        return identifier;
    }

    /**
     * Returns the default executor this event bus uses for dispatching events to subscribers.
     */
    final Executor executor() {
        return executor;
    }

    /**
     * Registers the specified subscriber to the event bus.  A subscribed object will be notified of any published
     * events on the methods annotated with the {@link Subscribe} annotation.
     *
     * <p> Each event handler method should take a single parameter indicating the type of event it wishes to receive.
     * When events are published on the bus, only subscribers who have an EventHandler method with a matching parameter
     * of the same type as the published event will receive the event notification from the bus.
     *
     * <p> Note that the EventBus maintains a {@link WeakReference} to the subscriber, but it is still advised to call
     * the {@link #unregister(Object)} method if the subscriber does not wish to receive events any longer.
     * @param object The object to subscribe to the event bus.
     */
    public void register(Object object) {
        subscriberRegistry.register(object);
    }

    /**
     * Unregisters all subscriber methods on a registered {@code object}.
     * @param object object whose subscriber methods should be unregistered.
     */
    public void unregister(Object object) {
        subscriberRegistry.unregister(object);
    }

    void unsubscribe(Subscriber subscriber) {
        subscriberRegistry.unregister(subscriber);
    }

    /**
     * Posts an event to all registered subscribers. This method will return successfully after the event has been
     * posted to all subscribers, and regardless of any exceptions thrown by subscribers.
     *
     * <p>If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a {@link
     * DeadEvent}, it will be wrapped in a DeadEvent and reposted.
     * @param event event to post.
     */
    public void publish(Object event) {
        Objects.requireNonNull(event);
        Iterator<Subscriber> eventSubscribers = subscriberRegistry.getSubscribers(event);
        if (eventSubscribers.hasNext()) {
            dispatcher.dispatch(event, eventSubscribers);
        } else if (!(event instanceof DeadEvent) && !(event instanceof ExceptionEvent)) {
            // the event had no subscribers and was not itself a DeadEvent or an ExceptionEvent
            publish(new DeadEvent(this, event));
        }
    }

    /**
     * Clear all subscribers from the cache.
     */
    public void close() {
        subscriberRegistry.clear();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{identifier=" + identifier + "}";
    }

    /**
     * Returns an {@link Executor} that runs each task in the thread that invokes {@link Executor#execute execute}, as
     * in {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}.
     */
    private static Executor createDirectExecutor() {
        return Runnable::run;
    }
}
