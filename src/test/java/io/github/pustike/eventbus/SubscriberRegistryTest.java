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

import java.util.Iterator;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

/**
 * Tests for {@link SubscriberRegistry}.
 * @author Colin Decker
 */
public class SubscriberRegistryTest extends TestCase {
    private final SubscriberRegistry registry = new SubscriberRegistry(new EventBus());

    public void testRegister() {
        assertEquals(0, registry.getSubscribersForTesting(String.class).size());

        registry.register(new StringSubscriber());
        assertEquals(1, registry.getSubscribersForTesting(String.class).size());

        registry.register(new StringSubscriber());
        assertEquals(2, registry.getSubscribersForTesting(String.class).size());

        registry.register(new ObjectSubscriber());
        assertEquals(2, registry.getSubscribersForTesting(String.class).size());
        assertEquals(1, registry.getSubscribersForTesting(Object.class).size());
    }

    public void testUnregister() {
        StringSubscriber s1 = new StringSubscriber();
        StringSubscriber s2 = new StringSubscriber();

        registry.register(s1);
        registry.register(s2);

        registry.unregister(s1);
        assertEquals(1, registry.getSubscribersForTesting(String.class).size());

        registry.unregister(s2);
        assertTrue(registry.getSubscribersForTesting(String.class).isEmpty());
    }

    public void testUnregister_notRegistered() {
        try {
            registry.unregister(new StringSubscriber());
        } catch (IllegalArgumentException expected) {
            fail();// Pustike: unlike guava, doesn't throw when unregistering a not-registered subscriber!
        }

        StringSubscriber s1 = new StringSubscriber();
        registry.register(s1);
        try {
            registry.unregister(new StringSubscriber());
        } catch (IllegalArgumentException expected) {
            // a StringSubscriber was registered, but not the same one we tried to unregister
            fail();
        }

        registry.unregister(s1);
        try {
            registry.unregister(s1);
        } catch (IllegalArgumentException expected) {
            fail();
        }
    }

    public void testGetSubscribers() {
        assertEquals(0, Iterators.size(registry.getSubscribers("")));

        registry.register(new StringSubscriber());
        assertEquals(1, Iterators.size(registry.getSubscribers("")));

        registry.register(new StringSubscriber());
        assertEquals(2, Iterators.size(registry.getSubscribers("")));

        registry.register(new ObjectSubscriber());
        assertEquals(3, Iterators.size(registry.getSubscribers("")));
        assertEquals(1, Iterators.size(registry.getSubscribers(new Object())));
        assertEquals(1, Iterators.size(registry.getSubscribers(1)));

        registry.register(new IntegerSubscriber());
        assertEquals(3, Iterators.size(registry.getSubscribers("")));
        assertEquals(1, Iterators.size(registry.getSubscribers(new Object())));
        assertEquals(2, Iterators.size(registry.getSubscribers(1)));
    }

    public void testGetSubscribers_returnsImmutableSnapshot() {
        StringSubscriber s1 = new StringSubscriber();
        StringSubscriber s2 = new StringSubscriber();
        ObjectSubscriber o1 = new ObjectSubscriber();

        Iterator<Subscriber> empty = registry.getSubscribers("");
        assertFalse(empty.hasNext());

        empty = registry.getSubscribers("");

        registry.register(s1);
        assertFalse(empty.hasNext());

        Iterator<Subscriber> one = registry.getSubscribers("");
        assertEquals(s1, one.next().getTarget());
        assertFalse(one.hasNext());

        one = registry.getSubscribers("");

        registry.register(s2);
        registry.register(o1);

        Iterator<Subscriber> three = registry.getSubscribers("");
        assertEquals(s1, one.next().getTarget());
        assertFalse(one.hasNext());

        assertEquals(s1, three.next().getTarget());
        assertEquals(s2, three.next().getTarget());
        assertEquals(o1, three.next().getTarget());
        assertFalse(three.hasNext());

        three = registry.getSubscribers("");

        registry.unregister(s2);

        assertEquals(s1, three.next().getTarget());
        assertEquals(s2, three.next().getTarget());
        assertEquals(o1, three.next().getTarget());
        assertFalse(three.hasNext());

        Iterator<Subscriber> two = registry.getSubscribers("");
        assertEquals(s1, two.next().getTarget());
        assertEquals(o1, two.next().getTarget());
        assertFalse(two.hasNext());
    }

    public static class StringSubscriber {
        @Subscribe
        public void handle(String s) {
        }
    }

    public static class IntegerSubscriber {
        @Subscribe
        public void handle(Integer i) {
        }
    }

    public static class ObjectSubscriber {
        @Subscribe
        public void handle(Object o) {
        }
    }

    public void testFlattenHierarchy() {
        assertEquals(ImmutableSet.of(
                        Object.class,
                        HierarchyFixtureInterface.class,
                        HierarchyFixtureSubinterface.class,
                        HierarchyFixtureParent.class,
                        HierarchyFixture.class),
                new DefaultSubscriberLoader().flattenHierarchyNotCached(HierarchyFixture.class));
    }

    private interface HierarchyFixtureInterface {
        // Exists only for hierarchy mapping; no members.
    }

    private interface HierarchyFixtureSubinterface
            extends HierarchyFixtureInterface {
        // Exists only for hierarchy mapping; no members.
    }

    private static class HierarchyFixtureParent
            implements HierarchyFixtureSubinterface {
        // Exists only for hierarchy mapping; no members.
    }

    private static class HierarchyFixture extends HierarchyFixtureParent {
        // Exists only for hierarchy mapping; no members.
    }
}
