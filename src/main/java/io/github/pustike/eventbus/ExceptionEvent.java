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

import java.lang.reflect.Method;
import java.util.EventObject;
import java.util.Objects;

/**
 * When any exceptions occur on the bus during handler execution, this event will be published.
 */
public final class ExceptionEvent extends EventObject {
    private static final long serialVersionUID = 1L;
    private final Object subscriber;
    private final Method subscriberMethod;
    private final Object event;
    private final Throwable cause;

    /**
     * Creates an exception event with all parameters.
     * @param eventBus the {@link EventBus} that handled the event and the subscriber.
     * @param subscriber the listener object on which the method is invoked.
     * @param subscriberMethod the subscribed method that threw the exception.
     * @param event the event object that caused the subscriber to throw.
     * @param cause the actual exception that caused the error.
     */
    public ExceptionEvent(EventBus eventBus, Object subscriber, Method subscriberMethod,
                          Object event, Throwable cause) {
        super(eventBus);
        this.subscriber = Objects.requireNonNull(subscriber);
        this.subscriberMethod = Objects.requireNonNull(subscriberMethod);
        this.event = Objects.requireNonNull(event);
        this.cause = cause;
    }

    /**
     * Get the {@link EventBus} that handled the event and the subscriber. Useful for broadcasting a new event based on
     * the error.
     * @return the {@link EventBus} that handled the event and the subscriber.
     */
    public EventBus getEventBus() {
        return (EventBus) getSource();
    }

    /**
     * Get the event object that caused the subscriber to throw.
     * @return the event object that caused the subscriber to throw.
     */
    public Object getEvent() {
        return event;
    }

    /**
     * Get the listener object on which the method is invoked.
     * @return the listener object on which the method is invoked.
     */
    public Object getSubscriber() {
        return subscriber;
    }

    /**
     * Get the subscribed method that threw the exception.
     * @return the subscribed method that threw the exception.
     */
    public Method getSubscriberMethod() {
        return subscriberMethod;
    }

    /**
     * Get the actual exception that caused the error.
     * @return the actual exception that caused the error.
     */
    public Throwable getCause() {
        return cause;
    }
}
