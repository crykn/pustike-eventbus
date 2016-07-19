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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link Dispatcher} implementations.
 * @author Colin Decker
 */
public class DispatcherTest extends TestCase {

    private final EventBus bus = new EventBus();

    private final IntegerSubscriber i1 = new IntegerSubscriber("i1");
    private final IntegerSubscriber i2 = new IntegerSubscriber("i2");
    private final IntegerSubscriber i3 = new IntegerSubscriber("i3");
    private final List<Subscriber> integerSubscribers = Arrays.asList(
            subscriber(bus, i1, "handleInteger", Integer.class),
            subscriber(bus, i2, "handleInteger", Integer.class),
            subscriber(bus, i3, "handleInteger", Integer.class));

    private final StringSubscriber s1 = new StringSubscriber("s1");
    private final StringSubscriber s2 = new StringSubscriber("s2");
    private final List<Subscriber> stringSubscribers = Arrays.asList(
            subscriber(bus, s1, "handleString", String.class),
            subscriber(bus, s2, "handleString", String.class));

    private final ConcurrentLinkedQueue<Object> dispatchedSubscribers
            = new ConcurrentLinkedQueue<>();

    private Dispatcher dispatcher;

    public void testPerThreadQueuedDispatcher() {
        dispatcher = Dispatcher.perThreadDispatchQueue();
        dispatcher.dispatch(1, integerSubscribers.iterator());

        assertThat(dispatchedSubscribers)
                .containsExactly(
                        i1, i2, i3, // Integer subscribers are dispatched to first.
                        s1, s2,     // Though each integer subscriber dispatches to all string subscribers,
                        s1, s2,     // those string subscribers aren't actually dispatched to until all integer
                        s1, s2      // subscribers have finished.
                ).inOrder();
    }

    public void testImmediateDispatcher() {
        dispatcher = Dispatcher.immediate();
        dispatcher.dispatch(1, integerSubscribers.iterator());

        assertThat(dispatchedSubscribers)
                .containsExactly(
                        i1, s1, s2,  // Each integer subscriber immediately dispatches to 2 string subscribers.
                        i2, s1, s2,
                        i3, s1, s2
                ).inOrder();
    }

    private static Subscriber subscriber(EventBus bus, Object target, String methodName, Class<?> eventType) {
        try {
            return Subscriber.create(bus, target, target.getClass().getMethod(methodName, eventType), 0);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    public final class IntegerSubscriber {
        private final String name;

        public IntegerSubscriber(String name) {
            this.name = name;
        }

        @Subscribe
        public void handleInteger(Integer integer) {
            dispatchedSubscribers.add(this);
            dispatcher.dispatch("hello", stringSubscribers.iterator());
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public final class StringSubscriber {
        private final String name;

        public StringSubscriber(String name) {
            this.name = name;
        }

        @Subscribe
        public void handleString(String string) {
            dispatchedSubscribers.add(this);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
