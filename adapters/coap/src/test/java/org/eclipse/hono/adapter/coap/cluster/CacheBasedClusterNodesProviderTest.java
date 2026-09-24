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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Tests verifying behavior of {@link CacheBasedCoapClusterNodeRegistry}
 * and {@link CacheBasedClusterNodesProvider}.
 */
public class CacheBasedClusterNodesProviderTest {

    private InMemoryTestCache cache;
    private TestClock clock;
    private CacheBasedCoapClusterNodeRegistry registry;
    private CacheBasedClusterNodesProvider provider;

    /**
     * Sets up test instances before each test.
     */
    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-09-24T00:00:00Z"));
        cache = new InMemoryTestCache(clock);
        registry = new CacheBasedCoapClusterNodeRegistry(cache, clock);
        provider = new CacheBasedClusterNodesProvider(registry);
    }

    /**
     * Verifies that registering a node allows retrieval via getClusterNode and available after refresh.
     */
    @Test
    void testRegisterNodeAndLookup() {
        final InetSocketAddress address = new InetSocketAddress("10.0.0.1", 5685);
        registry.registerNode(0, address, Duration.ofSeconds(30))
                .toCompletionStage().toCompletableFuture().join();

        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertEquals(address, provider.getClusterNode(0));
        assertTrue(provider.available(address));
        assertFalse(provider.available(new InetSocketAddress("10.0.0.2", 5685)));
        assertNull(provider.getClusterNode(1));
    }

    /**
     * Verifies that multiple registered nodes are returned by getAllNodes and refresh().
     */
    @Test
    void testMultipleNodesAndRefresh() {
        final InetSocketAddress addr0 = new InetSocketAddress("10.0.0.1", 5685);
        final InetSocketAddress addr1 = new InetSocketAddress("10.0.0.2", 5685);

        registry.registerNode(0, addr0, Duration.ofSeconds(30))
                .toCompletionStage().toCompletableFuture().join();
        registry.registerNode(1, addr1, Duration.ofSeconds(30))
                .toCompletionStage().toCompletableFuture().join();

        final Map<Integer, InetSocketAddress> allNodes = registry.getAllNodes()
                .toCompletionStage().toCompletableFuture().join();
        assertEquals(2, allNodes.size());
        assertEquals(addr0, allNodes.get(0));
        assertEquals(addr1, allNodes.get(1));

        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertEquals(addr0, provider.getClusterNode(0));
        assertEquals(addr1, provider.getClusterNode(1));
        assertTrue(provider.available(addr0));
        assertTrue(provider.available(addr1));
    }

    /**
     * Verifies that unregistering a node removes it from registry and provider.
     */
    @Test
    void testUnregisterNode() {
        final InetSocketAddress address = new InetSocketAddress("10.0.0.1", 5685);
        registry.registerNode(0, address, Duration.ofSeconds(30))
                .toCompletionStage().toCompletableFuture().join();

        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertEquals(address, provider.getClusterNode(0));
        assertTrue(provider.available(address));

        registry.unregisterNode(0).toCompletionStage().toCompletableFuture().join();
        assertNull(registry.getNodeAddress(0).toCompletionStage().toCompletableFuture().join());

        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertNull(provider.getClusterNode(0));
        assertFalse(provider.available(address));
    }

    /**
     * Verifies that heartbeat renews entry TTL in the cache.
     */
    @Test
    void testHeartbeatRenewal() {
        final InetSocketAddress address = new InetSocketAddress("10.0.0.1", 5685);
        registry.registerNode(0, address, Duration.ofSeconds(30))
                .toCompletionStage().toCompletableFuture().join();

        // Advance 20 seconds
        clock.advance(Duration.ofSeconds(20));

        // Renew heartbeat for another 30 seconds
        registry.heartbeat(0, Duration.ofSeconds(30))
                .toCompletionStage().toCompletableFuture().join();

        // Advance 20 more seconds (total 40 seconds, would have expired without heartbeat)
        clock.advance(Duration.ofSeconds(20));

        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertEquals(address, provider.getClusterNode(0));
        assertTrue(provider.available(address));
    }

    /**
     * Verifies that heartbeat for an unregistered node fails.
     */
    @Test
    void testHeartbeatForUnregisteredNodeFails() {
        final Future<Void> heartbeatFuture = registry.heartbeat(42, Duration.ofSeconds(30));
        assertTrue(heartbeatFuture.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    /**
     * Verifies that when a node expires in the cache, refresh() evicts it from local cache.
     */
    @Test
    void testEvictionWhenExpired() {
        final InetSocketAddress address = new InetSocketAddress("10.0.0.1", 5685);
        registry.registerNode(0, address, Duration.ofSeconds(10))
                .toCompletionStage().toCompletableFuture().join();

        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertEquals(address, provider.getClusterNode(0));
        assertTrue(provider.available(address));

        // Advance past expiration
        clock.advance(Duration.ofSeconds(15));

        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertNull(provider.getClusterNode(0));
        assertFalse(provider.available(address));
    }

    /**
     * Verifies that unrefreshed or unknown nodes return null non-blockingly.
     */
    @Test
    void testUnrefreshedOrUnknownNodeReturnsNull() {
        final InetSocketAddress address = new InetSocketAddress("10.0.0.3", 5685);
        registry.registerNode(3, address, Duration.ofSeconds(30))
                .toCompletionStage().toCompletableFuture().join();

        // Before provider.refresh(), getClusterNode(3) must return null non-blockingly
        assertNull(provider.getClusterNode(3));
        assertFalse(provider.available(address));

        // Unknown node also returns null immediately
        assertNull(provider.getClusterNode(99));

        // Once refresh() is called, node 3 becomes available
        provider.refresh().toCompletionStage().toCompletableFuture().join();
        assertEquals(address, provider.getClusterNode(3));
        assertTrue(provider.available(address));
    }

    /**
     * Verifies validation of arguments including node ID range (0..255).
     */
    @Test
    void testArgumentValidation() {
        assertThrows(NullPointerException.class, () -> new CacheBasedCoapClusterNodeRegistry(null));
        assertThrows(NullPointerException.class, () -> new CacheBasedClusterNodesProvider(null));

        final InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 5685);
        assertThrows(NullPointerException.class, () -> registry.registerNode(0, null, Duration.ofSeconds(10)));
        assertThrows(NullPointerException.class, () -> registry.registerNode(0, addr, null));
        assertThrows(IllegalArgumentException.class, () -> registry.registerNode(0, addr, Duration.ofSeconds(-1)));

        assertThrows(NullPointerException.class, () -> registry.heartbeat(0, null));
        assertThrows(IllegalArgumentException.class, () -> registry.heartbeat(0, Duration.ZERO));

        // Node ID range validation: negative
        assertThrows(IllegalArgumentException.class, () -> registry.registerNode(-1, addr, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> registry.heartbeat(-1, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> registry.unregisterNode(-1));
        assertThrows(IllegalArgumentException.class, () -> registry.getNodeAddress(-1));
        assertThrows(IllegalArgumentException.class, () -> CacheBasedCoapClusterNodeRegistry.toKey(-1));

        // Node ID range validation: > 255
        assertThrows(IllegalArgumentException.class, () -> registry.registerNode(256, addr, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> registry.heartbeat(256, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> registry.unregisterNode(256));
        assertThrows(IllegalArgumentException.class, () -> registry.getNodeAddress(256));
        assertThrows(IllegalArgumentException.class, () -> CacheBasedCoapClusterNodeRegistry.toKey(256));
    }

    /**
     * Mutable clock for deterministic time manipulation in tests.
     */
    private static class TestClock extends Clock {
        private final AtomicLong millis;

        TestClock(final Instant initialInstant) {
            this.millis = new AtomicLong(initialInstant.toEpochMilli());
        }

        void advance(final Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
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

    /**
     * In-memory cache implementation with TTL support for testing.
     */
    private static class InMemoryTestCache implements Cache<String, String> {

        private static class CacheEntry {
            final String value;
            final long expiresAt;

            CacheEntry(final String value, final long expiresAt) {
                this.value = value;
                this.expiresAt = expiresAt;
            }

            boolean isExpired(final long now) {
                return expiresAt > 0 && now >= expiresAt;
            }
        }

        private final ConcurrentMap<String, CacheEntry> store = new ConcurrentHashMap<>();
        private final TestClock clock;

        InMemoryTestCache(final TestClock clock) {
            this.clock = clock;
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
            final long expiresAt = lifespan > 0 ? clock.millis() + lifespanUnit.toMillis(lifespan) : -1;
            store.put(key, new CacheEntry(value, expiresAt));
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> putAll(final Map<? extends String, ? extends String> data) {
            return putAll(data, -1, TimeUnit.MILLISECONDS);
        }

        @Override
        public Future<Void> putAll(final Map<? extends String, ? extends String> data, final long lifespan,
                final TimeUnit lifespanUnit) {
            Objects.requireNonNull(data);
            data.forEach((k, v) -> put(k, v, lifespan, lifespanUnit));
            return Future.succeededFuture();
        }

        @Override
        public Future<String> get(final String key) {
            Objects.requireNonNull(key);
            final CacheEntry entry = store.get(key);
            if (entry == null) {
                return Future.succeededFuture(null);
            }
            if (entry.isExpired(clock.millis())) {
                store.remove(key, entry);
                return Future.succeededFuture(null);
            }
            return Future.succeededFuture(entry.value);
        }

        @Override
        public Future<Boolean> remove(final String key, final String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            final CacheEntry entry = store.get(key);
            if (entry == null || entry.isExpired(clock.millis())) {
                store.remove(key);
                return Future.succeededFuture(false);
            }
            if (Objects.equals(entry.value, value)) {
                store.remove(key, entry);
                return Future.succeededFuture(true);
            }
            return Future.succeededFuture(false);
        }

        @Override
        public Future<Map<String, String>> getAll(final Set<? extends String> keys) {
            Objects.requireNonNull(keys);
            final Map<String, String> result = new HashMap<>();
            final long now = clock.millis();
            for (final String key : keys) {
                final CacheEntry entry = store.get(key);
                if (entry != null) {
                    if (entry.isExpired(now)) {
                        store.remove(key, entry);
                    } else {
                        result.put(key, entry.value);
                    }
                }
            }
            return Future.succeededFuture(result);
        }
    }
}

