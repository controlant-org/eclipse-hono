/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.coap.cluster;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A mutable clock for deterministic time manipulation in tests.
 */
public final class TestClock extends Clock {

    private final AtomicLong millis;

    /**
     * Creates a new clock.
     *
     * @param initialInstant The instant that the clock starts at.
     */
    public TestClock(final Instant initialInstant) {
        this.millis = new AtomicLong(initialInstant.toEpochMilli());
    }

    /**
     * Advances the clock.
     *
     * @param duration The amount of time to advance the clock by.
     */
    public void advance(final Duration duration) {
        millis.addAndGet(duration.toMillis());
    }

    @Override
    public ZoneOffset getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis.get());
    }

    @Override
    public long millis() {
        return millis.get();
    }
}
