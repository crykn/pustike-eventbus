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

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test case for {@link EventBus}.
 * @author Cliff Biffle
 */
public class EventBusTest extends TestCase {
    private static final String EVENT = "Hello";
    private static final String BUS_IDENTIFIER = "test-bus";

    private EventBus bus;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        bus = new EventBus(BUS_IDENTIFIER);
    }

    public void testBasicCatcherDistribution() {
        StringCatcher catcher = new StringCatcher();
        bus.register(catcher);
        bus.publish(EVENT);

        List<String> events = catcher.getEvents();
        assertEquals("Only one event should be delivered.", 1, events.size());
        assertEquals("Correct string should be delivered.", EVENT, events.get(0));
    }

    /**
     * Tests that events are distributed to any subscribers to their type or any supertype, including interfaces and
     * superclasses.
     *
     * Also checks delivery ordering in such cases.
     */
    public void testPolymorphicDistribution() {
        // Three catchers for related types String, Object, and Comparable<?>.
        // String isa Object
        // String isa Comparable<?>
        // Comparable<?> isa Object
        StringCatcher stringCatcher = new StringCatcher();

        final List<Object> objectEvents = new ArrayList<>();
        Object objCatcher = new Object() {
            @SuppressWarnings("unused")
            @Subscribe
            public void eat(Object food) {
                objectEvents.add(food);
            }
        };

        final List<Comparable<?>> compEvents = new ArrayList<>();
        Object compCatcher = new Object() {
            @SuppressWarnings("unused")
            @Subscribe
            public <E> void eat(Comparable<E> food) {
                compEvents.add(food);
            }
        };
        bus.register(stringCatcher);
        bus.register(objCatcher);
        bus.register(compCatcher);

        // Two additional event types: Object and Comparable<?> (played by Integer)
        Object objEvent = new Object();
        Object compEvent = new Integer(6);

        bus.publish(EVENT);
        bus.publish(objEvent);
        bus.publish(compEvent);

        // Check the StringCatcher...
        List<String> stringEvents = stringCatcher.getEvents();
        assertEquals("Only one String should be delivered.",
                1, stringEvents.size());
        assertEquals("Correct string should be delivered.",
                EVENT, stringEvents.get(0));

        // Check the Catcher<Object>...
        assertEquals("Three Objects should be delivered.",
                3, objectEvents.size());
        assertEquals("String fixture must be first object delivered.",
                EVENT, objectEvents.get(0));
        assertEquals("Object fixture must be second object delivered.",
                objEvent, objectEvents.get(1));
        assertEquals("Comparable fixture must be thirdobject delivered.",
                compEvent, objectEvents.get(2));

        // Check the Catcher<Comparable<?>>...
        assertEquals("Two Comparable<?>s should be delivered.",
                2, compEvents.size());
        assertEquals("String fixture must be first comparable delivered.",
                EVENT, compEvents.get(0));
        assertEquals("Comparable fixture must be second comparable delivered.",
                compEvent, compEvents.get(1));
    }

    public void testSubscriberThrowsException() throws Exception {
        final RecordingSubscriberExceptionHandler exceptionHandler =
                new RecordingSubscriberExceptionHandler();
        final EventBus eventBus = new EventBus();
        eventBus.register(exceptionHandler);
        final RuntimeException exception =
                new RuntimeException("but culottes have a tendancy to ride up!");
        final Object subscriber = new Object() {
            @Subscribe
            public void throwExceptionOn(String message) {
                throw exception;
            }
        };
        eventBus.register(subscriber);
        eventBus.publish(EVENT);

        assertEquals("Cause should be available.",
                exception, exceptionHandler.exception);
        assertEquals("EventBus should be available.",
                eventBus, exceptionHandler.exceptionEvent.getEventBus());
        assertEquals("Event should be available.",
                EVENT,
                exceptionHandler.exceptionEvent.getEvent());
        assertEquals("Subscriber should be available.",
                subscriber, exceptionHandler.exceptionEvent.getSubscriber());
        assertEquals("Method should be available.",
                subscriber.getClass().getMethod("throwExceptionOn", String.class),
                exceptionHandler.exceptionEvent.getSubscriberMethod());
    }

    public void testSubscriberThrowsExceptionHandlerThrowsException() throws Exception {
        final EventBus eventBus = new EventBus();
        eventBus.register(new ThrowingSubscriberExceptionHandler());
        final Object subscriber = new Object() {
            @Subscribe
            public void throwExceptionOn(String message) {
                throw new RuntimeException();
            }
        };
        eventBus.register(subscriber);
        try {
            eventBus.publish(EVENT);
        } catch (RuntimeException e) {
            fail("Exception should not be thrown.");
        }
    }

    public void testDeadEventForwarding() {
        GhostCatcher catcher = new GhostCatcher();
        bus.register(catcher);

        // A String -- an event for which noone has registered.
        bus.publish(EVENT);

        List<DeadEvent> events = catcher.getEvents();
        assertEquals("One dead event should be delivered.", 1, events.size());
        assertEquals("The dead event should wrap the original event.",
                EVENT, events.get(0).getEvent());
    }

    public void testDeadEventPosting() {
        GhostCatcher catcher = new GhostCatcher();
        bus.register(catcher);

        bus.publish(new DeadEvent(this, EVENT));

        List<DeadEvent> events = catcher.getEvents();
        assertEquals("The explicit DeadEvent should be delivered.",
                1, events.size());
        assertEquals("The dead event must not be re-wrapped.",
                EVENT, events.get(0).getEvent());
    }

    public void testMissingSubscribe() {
        bus.register(new Object());
    }

    public void testUnregister() {
        StringCatcher catcher1 = new StringCatcher();
        StringCatcher catcher2 = new StringCatcher();
        try {
            bus.unregister(catcher1);
        } catch (IllegalArgumentException expected) {
            fail("Attempting to unregister an unregistered object succeeded");
            // Pustike: unlike guava, doesn't throw when unregistering a not-registered subscriber!
        }

        bus.register(catcher1);
        bus.publish(EVENT);
        bus.register(catcher2);
        bus.publish(EVENT);

        List<String> expectedEvents = new ArrayList<>();
        expectedEvents.add(EVENT);
        expectedEvents.add(EVENT);

        assertEquals("Two correct events should be delivered.",
                expectedEvents, catcher1.getEvents());

        assertEquals("One correct event should be delivered.",
                Collections.singletonList(EVENT), catcher2.getEvents());

        bus.unregister(catcher1);
        bus.publish(EVENT);

        assertEquals("Shouldn't catch any more events when unregistered.",
                expectedEvents, catcher1.getEvents());
        assertEquals("Two correct events should be delivered.",
                expectedEvents, catcher2.getEvents());

        try {
            bus.unregister(catcher1);
        } catch (IllegalArgumentException expected) {
            fail("Attempting to unregister an unregistered object succeeded");
            // Pustike: unlike guava, doesn't throw when unregistering a not-registered subscriber!
        }

        bus.unregister(catcher2);
        bus.publish(EVENT);
        assertEquals("Shouldn't catch any more events when unregistered.",
                expectedEvents, catcher1.getEvents());
        assertEquals("Shouldn't catch any more events when unregistered.",
                expectedEvents, catcher2.getEvents());
    }

    // NOTE: This test will always pass if register() is thread-safe but may also
    // pass if it isn't, though this is unlikely.
    public void testRegisterThreadSafety() throws Exception {
        List<StringCatcher> catchers = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int numberOfCatchers = 10000;
        for (int i = 0; i < numberOfCatchers; i++) {
            futures.add(executor.submit(new Registrator(bus, catchers)));
        }
        for (int i = 0; i < numberOfCatchers; i++) {
            futures.get(i).get();
        }
        assertEquals("Unexpected number of catchers in the list",
                numberOfCatchers, catchers.size());
        bus.publish(EVENT);
        List<String> expectedEvents = Collections.singletonList(EVENT);
        for (StringCatcher catcher : catchers) {
            assertEquals("One of the registered catchers did not receive an event.",
                    expectedEvents, catcher.getEvents());
        }
    }

    /**
     * Tests that bridge methods are not subscribed to events. In Java 8, annotations are included on the bridge method
     * in addition to the original method, which causes both the original and bridge methods to be subscribed (since
     * both are annotated @Subscribe) without specifically checking for bridge methods.
     */
    public void testRegistrationWithBridgeMethod() {
        final AtomicInteger calls = new AtomicInteger();
        bus.register(new Callback<String>() {
            @Subscribe
            @Override
            public void call(String s) {
                calls.incrementAndGet();
            }
        });

        bus.publish("hello");

        assertEquals(1, calls.get());
    }

    /**
     * Records thrown exception information.
     */
    private static final class RecordingSubscriberExceptionHandler {
        private ExceptionEvent exceptionEvent;
        private Throwable exception;

        @Subscribe
        public void handleException(ExceptionEvent exceptionEvent) {
            this.exceptionEvent = exceptionEvent;
            this.exception = exceptionEvent.getCause();
        }
    }
    private static final class ThrowingSubscriberExceptionHandler {
        @Subscribe
        public void handleException(ExceptionEvent exceptionEvent) {
            throw new RuntimeException();
        }
    }

    /**
     * Runnable which registers a StringCatcher on an event bus and adds it to a list.
     */
    private static class Registrator implements Runnable {
        private final EventBus bus;
        private final List<StringCatcher> catchers;

        Registrator(EventBus bus, List<StringCatcher> catchers) {
            this.bus = bus;
            this.catchers = catchers;
        }

        @Override
        public void run() {
            StringCatcher catcher = new StringCatcher();
            bus.register(catcher);
            catchers.add(catcher);
        }
    }

    /**
     * A collector for DeadEvents.
     * @author cbiffle
     */
    public static class GhostCatcher {
        private List<DeadEvent> events = new ArrayList<>();

        @Subscribe
        public void ohNoesIHaveDied(DeadEvent event) {
            events.add(event);
        }

        public List<DeadEvent> getEvents() {
            return events;
        }
    }

    public void testTypedEventSubscribers() {
        final EventBus eventBus = new EventBus();

        TypedEventSubscriber eventSubscriber = new TypedEventSubscriber();
        eventBus.register(eventSubscriber);

        eventBus.publish(new TypedEvent<>(""));
        eventBus.publish(new TypedEvent<>(6));

        assertEquals("One TypedEvent<String> should be delivered.", 1, eventSubscriber.typedStringEvents.size());
        assertEquals("One TypedEvent<Integer> should be delivered.", 1, eventSubscriber.typedIntEvents.size());
    }

    public static class TypedEventSubscriber {
        private List<TypedEvent<String>> typedStringEvents = new ArrayList<>();
        private List<TypedEvent<Integer>> typedIntEvents = new ArrayList<>();

        @Subscribe
        public void onTypedStringEvent(TypedEvent<String> event) {
            typedStringEvents.add(event);
        }

        @Subscribe
        public void onTypedIntEvent(TypedEvent<Integer> event) {
            typedIntEvents.add(event);
        }
    }

    private interface Callback<T> {
        void call(T t);
    }
}
