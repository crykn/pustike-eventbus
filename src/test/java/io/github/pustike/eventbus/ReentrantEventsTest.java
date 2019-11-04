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
package io.github.pustike.eventbus;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Validate that {@link EventBus} behaves carefully when listeners publish their own events.
 * @author Jesse Wilson
 */
public class ReentrantEventsTest extends TestCase {
    private static final String FIRST = "one";
    private static final Double SECOND = 2.0d;

    private final EventBus bus = new EventBus();

    public void testNoReentrantEvents() {
        ReentrantEventsHater hater = new ReentrantEventsHater();
        bus.register(hater);

        bus.publish(FIRST);

        assertEquals("ReentrantEventHater expected 2 events",
            List.of(FIRST, SECOND), hater.eventsReceived);
    }

    public class ReentrantEventsHater {
        boolean ready = true;
        final List<Object> eventsReceived = new ArrayList<>();

        @Subscribe
        public void listenForStrings(String event) {
            eventsReceived.add(event);
            ready = false;
            try {
                bus.publish(SECOND);
            } finally {
                ready = true;
            }
        }

        @Subscribe
        public void listenForDoubles(Double event) {
            assertTrue("I received an event when I wasn't ready!", ready);
            eventsReceived.add(event);
        }
    }

    public void testEventOrderingIsPredictable() {
        EventProcessor processor = new EventProcessor();
        bus.register(processor);

        EventRecorder recorder = new EventRecorder();
        bus.register(recorder);

        bus.publish(FIRST);

        assertEquals("EventRecorder expected events in order",
            List.of(FIRST, SECOND), recorder.eventsReceived);
    }

    public class EventProcessor {
        @Subscribe
        public void listenForStrings(String event) {
            bus.publish(SECOND);
        }
    }

    public class EventRecorder {
        final List<Object> eventsReceived = new ArrayList<>();

        @Subscribe
        public void listenForEverything(Object event) {
            eventsReceived.add(event);
        }
    }
}
