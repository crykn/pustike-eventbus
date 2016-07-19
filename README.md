Pustike EventBus
================
Pustike EventBus is a fork of [Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained), which is probably the most commonly known event bus for Java. Most of the documentation here and test cases are from Guava itself.

The [Guava Project](https://github.com/google/guava) contains several core libraries and is distributed as a single module that has a size of ~2.2MB (as of v19.0). So an application using only the EventBus will also need to include the full Guava dependency.

Pustike EventBus is an effort to extract only the event bus library from Guava project without any other dependencies. And it also provides few additional features / changes, like:
* Typed Events supporting event type specific subscribers
* Error handling using ExceptionEvents
* WeakReference to target subscribers
* Unregistering a not-registered subscriber doesn't throw exception
* Only ~20kB in size and has no additional dependencies
* Java 8 as the min requirement (Guava supports Java 6 onwards)

Event Bus
---------
An event bus is a library providing publisher/subscriber pattern for loose coupling between components(event senders and receivers), by sending messages to each other indirectly. Some objects register with the bus to be notified when certain events of interest occur. And some publish events on the bus. The bus notifies each of the registrants when the event is published. So the registrant objects and the event-source objects need not know about each other directly. Each may join or depart the bus at any time. Thus it enables central communication between components, simplifies the code and removes direct dependencies.

To create an instance of event bus with a default identifier and using a per thread dispatch queue and a direct executor which publishes events in the same thread:
```java
EventBus bus = new EventBus();
```

### Dispatcher
Dispatcher is used for dispatching events to subscribers, providing different event ordering guarantees that make sense for different situations. Please note that the dispatcher controls the order in which events are dispatched, while the executor controls how (i.e. on which thread) the subscriber is actually called when an event is dispatched to it. Two types of dispatchers are supported:

1. **Per Thread Dispatch Queue**: a dispatcher that queues events that are posted reentrantly on a thread that is already dispatching an event, guaranteeing that all events posted on a single thread are dispatched to all subscribers in the order they are posted.
   When all subscribers are dispatched to using a direct executor (which dispatches on the same thread that posts the event), this yields a breadth-first dispatch order on each thread. That is, all subscribers to a single event A will be called before any subscribers to any events B and C that are posted to the event bus by the subscribers to A.
2. **Immediate Dispatcher**: dispatches events to subscribers immediately as they're posted without using an intermediate queue to change the dispatch order. This is effectively a depth-first dispatch order, vs. breadth-first when using a queue. If an subscriber method generates a new event, this dispatcher will deliver that event first and then continues deliver the original event to other listeners.

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

### Publishing
Publish events on the bus which dispatches it to all listeners subscribing to this event type. An instance of any class may be published and it will only be dispatched to subscribers for that type.
```java
String messageEvent = "Hello!";
bus.publish(messageEvent);
```
* **Event Hierarchy**: When an event is published, it is delivered to all the subscribers matching to any of the super classes or interfaces of this event. For ex. when `String` and `Integer` type events are published, the subscriber to `Comparable` will be called on both the events. This can be used to create more generic listeners listening for a broader range of events and more detailed ones for specific purposes.

### Unregister

Listeners can be unregistered from the bus using
```java
bus.unregister(listener);
```

### Special Event Types
* **Typed Event**: Instead of creating separate classes for each type of events, the TypedEvent provides a type aware event with an optional context information. This event will be delivered to only those subscriber methods matching to it's actual type. For example: in place of creating `CustomerEvent`, `VendorEvent`, etc, the `TypedEvent<Customer>`, `TypedEvent<Vendor>` can be used. The context information can be useful in communicating the state of the object, like `new TypedEvent<>(customer, "MODIFIED");`
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

### Using external Cache
By default, the event bus will use a ConcurrentMap internally to cache Subscriber methods identified on registering listener objects. To use an external cache like [Caffeine](https://github.com/ben-manes/caffeine) for loading & storing subscriber methods in a listener class and type hierarchy of event classes, the following approach can be used:
```java
LoadingCache<Class<?>, List<Method>> subscriberMethodCache = Caffeine.newBuilder()
        .weakKeys().build(SubscriberRegistry::getAnnotatedMethodsNotCached);
LoadingCache<Class<?>, Set<Class<?>>> typeHierarchyCache = Caffeine.newBuilder()
        .weakKeys().weakValues().build(SubscriberRegistry::flattenHierarchyNotCached);
Executor executor = Runnable::run; // a simple direct executor!
EventBus eventBus = new EventBus("default", Dispatcher.perThreadDispatchQueue(), executor,
        subscriberMethodCache::get, typeHierarchyCache::get);
```

Other EventBus Libraries
------------------------
* [Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained) allows publish-subscribe-style event communication

* [Square](http://square.github.io/otto)'s [Otto](http://github.com/square/otto): An enhanced event bus with emphasis on Android support.

* GreenRobot [Event Bus](https://github.com/greenrobot/EventBus): Android optimized event bus that simplifies communication between Activities, Fragments, Threads, Services, etc. Less code, better quality.

* [MBassador](https://github.com/bennidi/mbassador) is a light-weight, high-performance message (event) bus implementation based on the publish subscribe pattern.

* [EventBus](http://www.eventbus.org) and [Simple EventBus](http://code.google.com/p/simpleeventbus)

* [Mycila PubSub](https://github.com/mycila/pubsub) is a new powerful event framework for in-memory event management.

* [Spring Framework](http://docs.spring.io/spring/docs/current/spring-framework-reference/htmlsingle/#context-functionality-events) Application Event publishing support using spring context.

License
-------
This library is published under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
```
 Copyright (C) 2016 the original author or authors.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
```
