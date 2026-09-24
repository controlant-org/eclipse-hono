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

package org.eclipse.hono.adapter.coap.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.SecretKey;

import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.SessionId;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.util.SecretUtil;
import org.eclipse.hono.adapter.coap.CoapAdapterMetrics;
import org.eclipse.hono.deviceconnection.common.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

/**
 * Tests verifying behavior of {@link CacheBasedDtlsSessionStore}.
 */
public class CacheBasedDtlsSessionStoreTest {

    private InMemoryTestCache cache;
    private TestClock clock;
    private CoapAdapterMetrics metrics;
    private CacheBasedDtlsSessionStore store;

    /**
     * Sets up test fixtures before each test.
     */
    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-09-24T00:00:00Z"));
        cache = new InMemoryTestCache(clock);
        metrics = mock(CoapAdapterMetrics.class);
        store = new CacheBasedDtlsSessionStore(cache, metrics, Duration.ofHours(24), Duration.ofSeconds(2));
    }

    /**
     * Verifies that storing and retrieving a session returns an equivalent restored session
     * and reports resumption success.
     *
     * @throws Exception if reflection fails.
     */
    @Test
    void testPutAndGetRoundtrip() throws Exception {
        final byte[] sessionIdBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        final DTLSSession session = createSession(sessionIdBytes);

        store.put(session);

        final SessionId sessionId = session.getSessionIdentifier();
        final String expectedKey = CacheBasedDtlsSessionStore.toKey(sessionId);
        assertNotNull(cache.get(expectedKey).toCompletionStage().toCompletableFuture().join());

        final DTLSSession restored = store.get(sessionId);
        assertNotNull(restored);
        assertEquals(session.getSessionIdentifier(), restored.getSessionIdentifier());
        assertEquals(session.getCipherSuite(), restored.getCipherSuite());
        assertEquals(session, restored);

        verify(metrics).incrementResumption(true);
    }

    /**
     * Verifies that removing a session deletes it from the cache and subsequent get returns null.
     *
     * @throws Exception if reflection fails.
     */
    @Test
    void testRemoveSession() throws Exception {
        final byte[] sessionIdBytes = new byte[] { 10, 20, 30, 40 };
        final DTLSSession session = createSession(sessionIdBytes);

        store.put(session);
        final SessionId sessionId = session.getSessionIdentifier();
        assertNotNull(store.get(sessionId));

        store.remove(sessionId);

        final DTLSSession afterRemove = store.get(sessionId);
        assertNull(afterRemove);
        verify(metrics).incrementResumption(false);
    }

    /**
     * Verifies that get on an unknown session ID returns null and reports resumption failure.
     */
    @Test
    void testCacheMiss() {
        final SessionId unknownId = new SessionId(new byte[] { 99, 98, 97 });
        final DTLSSession result = store.get(unknownId);

        assertNull(result);
        verify(metrics).incrementResumption(false);
    }

    /**
     * Verifies that corrupted payload (invalid Base64) in the cache returns null safely
     * without throwing an exception and reports resumption failure.
     */
    @Test
    void testCorruptedBase64Payload() {
        final SessionId sessionId = new SessionId(new byte[] { 1, 1, 1, 1 });
        final String key = CacheBasedDtlsSessionStore.toKey(sessionId);
        cache.put(key, "!!!not-valid-base64!!!").toCompletionStage().toCompletableFuture().join();

        final DTLSSession result = store.get(sessionId);

        assertNull(result);
        verify(metrics).incrementResumption(false);
    }

    /**
     * Verifies that corrupted session bytes (valid Base64 but invalid DTLSSession bytes)
     * returns null safely without throwing an exception and reports resumption failure.
     */
    @Test
    void testCorruptedSessionBytes() {
        final SessionId sessionId = new SessionId(new byte[] { 2, 2, 2, 2 });
        final String key = CacheBasedDtlsSessionStore.toKey(sessionId);
        final String corruptedBase64 = Base64.getEncoder().encodeToString(new byte[] { 0, 1, 2, 3 });
        cache.put(key, corruptedBase64).toCompletionStage().toCompletableFuture().join();

        final DTLSSession result = store.get(sessionId);

        assertNull(result);
        verify(metrics).incrementResumption(false);
    }

    /**
     * Verifies that cache get failure returns null safely and reports resumption failure.
     */
    @Test
    void testCacheGetFailureGracefulHandling() {
        final SessionId sessionId = new SessionId(new byte[] { 3, 3, 3, 3 });
        cache.setFailGet(true);

        final DTLSSession result = store.get(sessionId);

        assertNull(result);
        verify(metrics).incrementResumption(false);
    }

    /**
     * Verifies that cache get timeout returns null safely and reports resumption failure.
     */
    @Test
    void testCacheGetTimeoutGracefulHandling() {
        final SessionId sessionId = new SessionId(new byte[] { 4, 4, 4, 4 });
        final CacheBasedDtlsSessionStore timeoutStore = new CacheBasedDtlsSessionStore(
                cache, metrics, Duration.ofHours(24), Duration.ofMillis(50));
        cache.setHangGet(true);

        final DTLSSession result = timeoutStore.get(sessionId);

        assertNull(result);
        verify(metrics).incrementResumption(false);
    }

    /**
     * Verifies that put with null or empty session does not store anything.
     */
    @Test
    void testPutNullOrEmptySession() {
        store.put(null);

        final DTLSSession emptySession = new DTLSSession();
        store.put(emptySession);

        verifyNoInteractions(metrics);
    }

    /**
     * Verifies that get with null or empty session ID returns null.
     */
    @Test
    void testGetNullOrEmptySessionId() {
        assertNull(store.get(null));
        assertNull(store.get(SessionId.emptySessionId()));
        assertNull(store.get(new SessionId(new byte[0])));

        verifyNoInteractions(metrics);
    }

    /**
     * Verifies that remove with null or empty session ID does not throw.
     */
    @Test
    void testRemoveNullOrEmptySessionId() {
        store.remove(null);
        store.remove(SessionId.emptySessionId());
        store.remove(new SessionId(new byte[0]));

        verifyNoInteractions(metrics);
    }

    /**
     * Verifies that configured TTL is applied when storing sessions in the cache,
     * and expired sessions are not returned.
     *
     * @throws Exception if reflection fails.
     */
    @Test
    void testSessionTtlAndExpiration() throws Exception {
        final Duration customTtl = Duration.ofHours(12);
        final CacheBasedDtlsSessionStore customStore = new CacheBasedDtlsSessionStore(
                cache, metrics, customTtl, Duration.ofSeconds(2));

        final DTLSSession session = createSession(new byte[] { 5, 5, 5, 5 });
        customStore.put(session);

        final SessionId sessionId = session.getSessionIdentifier();
        assertEquals(customTtl.toMillis(), cache.getLastLifespanMs());

        // Still valid before TTL expires
        clock.advance(Duration.ofHours(11));
        assertNotNull(customStore.get(sessionId));

        // Expired after TTL
        clock.advance(Duration.ofHours(2));
        assertNull(customStore.get(sessionId));
    }

    /**
     * Verifies argument validation in constructors and methods.
     */
    @Test
    void testArgumentValidation() {
        assertThrows(NullPointerException.class, () -> new CacheBasedDtlsSessionStore(null));
        assertThrows(NullPointerException.class, () -> new CacheBasedDtlsSessionStore(cache, metrics, null, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> new CacheBasedDtlsSessionStore(cache, metrics, Duration.ofHours(1), null));

        assertThrows(IllegalArgumentException.class, () -> new CacheBasedDtlsSessionStore(
                cache, metrics, Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new CacheBasedDtlsSessionStore(
                cache, metrics, Duration.ofSeconds(-1), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new CacheBasedDtlsSessionStore(
                cache, metrics, Duration.ofHours(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new CacheBasedDtlsSessionStore(
                cache, metrics, Duration.ofHours(1), Duration.ofSeconds(-1)));

        assertThrows(NullPointerException.class, () -> CacheBasedDtlsSessionStore.toKey(null));

        final SessionId sessionId = new SessionId(new byte[] { 0x0A, 0x0B });
        assertEquals("coap:session:" + sessionId.getAsString(), CacheBasedDtlsSessionStore.toKey(sessionId));
    }

    /**
     * Verifies that constructor without metrics defaults to no-op metrics.
     *
     * @throws Exception if reflection fails.
     */
    @Test
    void testDefaultConstructorWithoutMetrics() throws Exception {
        final CacheBasedDtlsSessionStore noMetricsStore = new CacheBasedDtlsSessionStore(cache);
        final DTLSSession session = createSession(new byte[] { 9, 9, 9 });
        noMetricsStore.put(session);

        final DTLSSession restored = noMetricsStore.get(session.getSessionIdentifier());
        assertNotNull(restored);
        assertEquals(session.getSessionIdentifier(), restored.getSessionIdentifier());
    }

    private static DTLSSession createSession(final byte[] sessionIdBytes) throws Exception {
        final DTLSSession session = new DTLSSession();
        final SessionId sessionId = new SessionId(sessionIdBytes);
        final Method setSessionIdentifier = DTLSSession.class.getDeclaredMethod("setSessionIdentifier", SessionId.class);
        setSessionIdentifier.setAccessible(true);
        setSessionIdentifier.invoke(session, sessionId);
        final Method setCipherSuite = DTLSSession.class.getDeclaredMethod("setCipherSuite", CipherSuite.class);
        setCipherSuite.setAccessible(true);
        setCipherSuite.invoke(session, CipherSuite.TLS_PSK_WITH_AES_128_CCM_8);
        final SecretKey masterSecret = SecretUtil.create(new byte[48], "PSK");
        final Method setMasterSecret = DTLSSession.class.getDeclaredMethod("setMasterSecret", SecretKey.class);
        setMasterSecret.setAccessible(true);
        setMasterSecret.invoke(session, masterSecret);
        return session;
    }

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

    private static class InMemoryTestCache implements Cache<String, String> {

        private static class CacheEntry {
            final String value;
            final long expiresAt;
            final long lifespanMs;

            CacheEntry(final String value, final long expiresAt, final long lifespanMs) {
                this.value = value;
                this.expiresAt = expiresAt;
                this.lifespanMs = lifespanMs;
            }

            boolean isExpired(final long now) {
                return expiresAt > 0 && now >= expiresAt;
            }
        }

        private final ConcurrentMap<String, CacheEntry> store = new ConcurrentHashMap<>();
        private final TestClock clock;
        private final AtomicLong lastLifespanMs = new AtomicLong(-1);
        private volatile boolean failGet;
        private volatile boolean hangGet;

        InMemoryTestCache(final TestClock clock) {
            this.clock = clock;
        }

        void setFailGet(final boolean failGet) {
            this.failGet = failGet;
        }

        void setHangGet(final boolean hangGet) {
            this.hangGet = hangGet;
        }

        long getLastLifespanMs() {
            return lastLifespanMs.get();
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
            final long lifespanMs = lifespan > 0 ? lifespanUnit.toMillis(lifespan) : -1;
            lastLifespanMs.set(lifespanMs);
            final long expiresAt = lifespanMs > 0 ? clock.millis() + lifespanMs : -1;
            store.put(key, new CacheEntry(value, expiresAt, lifespanMs));
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
            if (failGet) {
                return Future.failedFuture(new RuntimeException("Simulated cache get error"));
            }
            if (hangGet) {
                return Promise.<String>promise().future();
            }
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

