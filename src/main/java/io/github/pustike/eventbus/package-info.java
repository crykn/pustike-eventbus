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
/**
 * Pustike EventBus is a fork of <a href= "https://github.com/google/guava/wiki/EventBusExplained">Guava EventBus</a>,
 * which is probably the most commonly known event bus for Java. Most of the documentation here and test cases are from
 * Guava itself.
 *
 * <p>The [Guava Project](https://github.com/google/guava) contains several core libraries and is distributed as a
 * single module that has a size of ~2.2Mb (as of v19.0). So an application using only the EventBus will also need to
 * include the full Guava dependency.
 *
 * <p>Pustike EventBus is an effort to extract only the event bus library from Guava project without any other
 * dependencies. And it also provides few additional features / changes, like: <li>Typed Events supporting event type
 * specific subscribers <li>Error handling using ExceptionEvents <li>WeekReference to target subscribers
 * <li>Unregistering a not-registered subscriber doesn't throw exception <li>Only ~20k in size and has no additional
 * dependencies <li>Java 8 as the min requirement (Guava supports Java 6 onwards)
 */
package io.github.pustike.eventbus;