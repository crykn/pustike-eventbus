## guava-eventbus
[![Release](https://jitpack.io/v/crykn/guava-eventbus.svg)](https://jitpack.io/#crykn/guava-eventbus)

This is a fork of [pustike-eventbus](https://github.com/pustike/pustike-eventbus), an extracted version of Guava's well known [`EventBus`](https://github.com/google/guava/wiki/EventBusExplained). Most of the documentation  and test cases here are from Guava itself. If you are looking for a GWT-compatible version, check out [gdx-guava-eventbus](https://github.com/crykn/gdx-guava-eventbus).

The [Guava Project](https://github.com/google/guava) contains several core libraries and is distributed as a single module that has a size of ~2.7MB (as of v29.0). So an application using only the EventBus will also need to include the full Guava dependency.

This project is an effort to extract only the event bus library from Guava without any other dependencies. It also provides a few additional features/changes, like:
* Typed Events supporting event type specific subscribers
* Error handling using ExceptionEvents
* WeakReference to target subscribers
* `@Subscribe(threadSafe = true)` instead of `@AllowConcurrentEvents`
* Unregistering a not-registered subscriber doesn't throw exception
* Allows using an external cache for loading subscriber methods and event type hierarchy
* Only ~20kB in size when using default subscriber cache

## Event Bus
An event bus is a library providing publisher/subscriber pattern for loose coupling between components(event senders and receivers), by sending messages to each other indirectly. Some objects register with the bus to be notified when certain events of interest occur. And some post events on the bus. The bus notifies each of the registered subscribers when the event is published. This enables communication between components via a common channel, which in turn simplifies the code and removes direct dependencies.

To create an instance of the event bus with a default identifier and using a per thread dispatch queue and a direct executor which publishes events in the same thread:
```java
EventBus bus = new EventBus();
```

### Dispatcher
Dispatcher is used for dispatching events to subscribers, providing different event ordering guarantees that make sense for different situations. Please note that the dispatcher controls the order in which events are dispatched, while the executor controls how (i.e. on which thread) the subscriber is actually called when an event is dispatched to it. Two types of dispatchers are supported:

1. **Per Thread Dispatch Queue**: a dispatcher that queues events that are posted reentrantly on a thread that is already dispatching an event, guaranteeing that all events posted on a single thread are dispatched to all subscribers in the order they are posted.
   When all subscribers are dispatched to using a direct executor (which dispatches on the same thread that posts the event), this yields a breadth-first dispatch order on each thread. That is, all subscribers to a single event A will be called before any subscribers to any events B and C that are posted to the event bus by the subscribers to A.
2. **Immediate Dispatcher**: dispatches events to subscribers immediately as they're posted without using an intermediate queue to change the dispatch order. This is effectively a depth-first dispatch order, vs. breadth-first when using a queue.

### Subscribing
Subscribe to events by registering listeners to the bus. Subscriber methods in a listener should be annotated with `@Subscribe` and the method should take only a single parameter, the type of which will be the event to subscribe to. The registry will traverse the listener class hierarchy and add methods from base classes or interfaces that are annotated.
```java
Object listener = new Object() {
    @Subscribe
    public void onMessageEvent(String message) {
        // process the message here...
    }
};
bus.register(listener);
```
Here internally weak references to listener objects are maintained instead of strong references, as in Gauava.

* **Thread Safety**: By default, the bus considers subscriber methods as not thread safe and synchronizes invocations of the subscriber method to ensure that only one thread may enter the method at a time. To mark an event subscriber method as being thread-safe, set `@Subscribe(threadSafe = true)`. It indicates that the bus may invoke this event subscriber simultaneously from multiple threads.

### Posting Events
Posting an event on the bus dispatches it to all listeners subscribing to this specific event type.
```java
String messageEvent = "Hello!";
bus.post(messageEvent);
```
* **Event Hierarchy**: When an event is published, it is delivered to all the subscribers matching to any of the super classes or interfaces of this event. For example, when `String` and `Integer` type events are published, the subscriber to `Comparable` will be called on both the events. This can be used to create more generic listeners listening for a broader range of events and more detailed ones for specific purposes.

### Unregistering
Listeners can be unregistered from the bus using
```java
bus.unregister(listener);
```

### Special Event Types
* **Typed Event**: Instead of creating separate classes for each type of events, the TypedEvent provides a type aware event with an optional context information. This event will be delivered to only those subscriber methods matching to its actual type. For example: in place of creating `CustomerEvent`, `VendorEvent`, etc, the `TypedEvent<Customer>`, `TypedEvent<Vendor>` can be used. The context information can be useful in communicating the state of the object, like `new TypedEvent<>(customer, "MODIFIED");`
```java
    @Subscribe
    public void onTypedStringEvent(TypedEvent<String> event) {
        // event.getSource();// the object of the event.
        // event.getType();// the type of the object.
        // event.getContext();// the event context if available, can be null.
    }
    // ... and publish the event as following
    bus.publish(new TypedEvent<>("Hello!"));// note that this will not be matched to TypedEvent<Integer> subscriber!
```
* **Dead Event**: is a wrapper for an event that was posted, but which had no subscribers and thus could not be delivered. Registering a DeadEvent subscriber is useful for debugging or logging, as it can detect mis-configurations in a system's event distribution.
```java
    @Subscribe
    public void onDeadEvent(DeadEvent deadEvent) {
        // deadEvent.getEvent(); // the event that could not be delivered.
    }
```
* **ExceptionEvent**: When any exceptions occur on the bus during handler execution, this event will be published. This is useful for logging and should be handled by the application. It provides access to the listener object, subscriber method, the posted event and the original exception that caused this event to be published. This can be handled similar to any other event as:
```java
    @Subscribe
    public void onException(ExceptionEvent exceptionEvent) {
        // process the exception info here...
    }
```

### Using an External Cache
By default, the event bus will use a ConcurrentMap internally to cache Subscriber methods identified on registering listener objects. To use an external cache like [Caffeine](https://github.com/ben-manes/caffeine) for loading & storing subscriber methods in a listener class and type hierarchy of event classes, the following approach can be used:
```java
public class CaffeineSubscriberLoader extends DefaultSubscriberLoader {
    private final LoadingCache<Class<?>, List<Method>> subscriberMethodCache;
    private final LoadingCache<Class<?>, Set<Class<?>>> typeHierarchyCache;

    public CaffeineSubscriberLoader() {
        this.subscriberMethodCache = Caffeine.newBuilder().weakKeys()
                .build(super::findAnnotatedMethodsNotCached);
        this.typeHierarchyCache = Caffeine.newBuilder().weakKeys().weakValues()
                .build(super::flattenHierarchyNotCached);
    }

    @Override
    public List<Method> findSubscriberMethods(Class<?> clazz) {
        return subscriberMethodCache.get(clazz);
    }

    @Override
    public Set<Class<?>> flattenHierarchy(Class<?> clazz) {
        return typeHierarchyCache.get(clazz);
    }

    @Override
    public void invalidateAll() {
        subscribeMethodsCache.invalidateAll();
        typeHierarchyCache.invalidateAll();
    }
}
// and create the event bus using this cache
CaffeineSubscriberLoader subscriberLoader = new CaffeineSubscriberLoader();
Executor executor = Runnable::run; // a simple direct executor!
EventBus eventBus = new EventBus("default", Dispatcher.perThreadDispatchQueue(),
        executor, subscriberLoader);
```

## License
This library is published under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
```
 Copyright (C) 2016 the original author or authors.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
```
