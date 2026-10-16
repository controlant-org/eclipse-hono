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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceconnection.common.Cache;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * An in-memory cache with support for entry lifespans for testing.
 */
public final class InMemoryTestCache implements Cache<String, String> {

    private final ConcurrentMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final Clock clock;

    /**
     * Creates a new cache.
     *
     * @param clock The clock to use for determining if entries have expired.
     */
    public InMemoryTestCache(final Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    private record CacheEntry(String value, long expiresAt) {

        boolean isExpired(final long now) {
            return expiresAt > 0 && now >= expiresAt;
        }
    }

    private CacheEntry newEntry(final String value, final long lifespan, final TimeUnit lifespanUnit) {
        final long expiresAt = lifespan > 0 ? clock.millis() + lifespanUnit.toMillis(lifespan) : -1;
        return new CacheEntry(value, expiresAt);
    }

    private CacheEntry getUnexpired(final String key) {
        final CacheEntry entry = store.get(key);
        if (entry != null && entry.isExpired(clock.millis())) {
            store.remove(key, entry);
            return null;
        }
        return entry;
    }

    @Override
    public Future<JsonObject> checkForCacheAvailability() {
        return Future.succeededFuture(new JsonObject().put("status", "UP"));
    }

    @Override
    public Future<Void> put(final String key, final String value) {
        return put(key, value, -1, TimeUnit.MILLISECONDS);
    }

    @Override
    public Future<Void> put(final String key, final String value, final long lifespan, final TimeUnit lifespanUnit) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        store.put(key, newEntry(value, lifespan, lifespanUnit));
        return Future.succeededFuture();
    }

    @Override
    public Future<Boolean> putIfAbsent(
            final String key,
            final String value,
            final long lifespan,
            final TimeUnit lifespanUnit) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        getUnexpired(key);
        return Future.succeededFuture(store.putIfAbsent(key, newEntry(value, lifespan, lifespanUnit)) == null);
    }

    @Override
    public Future<Void> putAll(final Map<? extends String, ? extends String> data) {
        return putAll(data, -1, TimeUnit.MILLISECONDS);
    }

    @Override
    public Future<Void> putAll(
            final Map<? extends String, ? extends String> data,
            final long lifespan,
            final TimeUnit lifespanUnit) {
        Objects.requireNonNull(data);
        data.forEach((k, v) -> put(k, v, lifespan, lifespanUnit));
        return Future.succeededFuture();
    }

    @Override
    public Future<String> get(final String key) {
        Objects.requireNonNull(key);
        final CacheEntry entry = getUnexpired(key);
        return Future.succeededFuture(entry == null ? null : entry.value());
    }

    @Override
    public Future<Boolean> remove(final String key, final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        final CacheEntry entry = getUnexpired(key);
        if (entry != null && entry.value().equals(value)) {
            return Future.succeededFuture(store.remove(key, entry));
        }
        return Future.succeededFuture(false);
    }

    @Override
    public Future<Map<String, String>> getAll(final Set<? extends String> keys) {
        Objects.requireNonNull(keys);
        final Map<String, String> result = new HashMap<>();
        for (final String key : keys) {
            final CacheEntry entry = getUnexpired(key);
            if (entry != null) {
                result.put(key, entry.value());
            }
        }
        return Future.succeededFuture(result);
    }
}
