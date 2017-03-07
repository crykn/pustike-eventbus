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

import java.util.EventObject;
import java.util.Objects;

/**
 * Wraps an event that was posted, but which had no subscribers and thus could not be delivered.
 *
 * <p> Registering a DeadEvent subscriber is useful for debugging or logging, as it can detect misconfigurations in a
 * system's event distribution.
 * @author Cliff Biffle
 */
public final class DeadEvent extends EventObject {
    private final Object event;

    /**
     * Creates a new DeadEvent.
     * @param source object broadcasting the DeadEvent (generally the {@link EventBus}).
     * @param event the event that could not be delivered.
     */
    public DeadEvent(Object source, Object event) {
        super(Objects.requireNonNull(source));
        this.event = Objects.requireNonNull(event);
    }

    /**
     * Returns the wrapped, 'dead' event, which the system was unable to deliver to any registered subscriber.
     * @return the 'dead' event that could not be delivered.
     */
    public Object getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{source=" + source + ", event=" + event + "}";
    }
}
