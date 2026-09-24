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

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.californium.elements.util.DatagramReader;
import org.eclipse.californium.elements.util.DatagramWriter;
import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.SessionId;
import org.eclipse.californium.scandium.dtls.SessionStore;
import org.eclipse.hono.adapter.coap.CoapAdapterMetrics;
import org.eclipse.hono.deviceconnection.common.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

/**
 * A {@link SessionStore} implementation backed by a central {@link Cache}.
 * <p>
 * DTLS sessions are serialized using Californium's native binary format, Base64-encoded,
 * and persisted in the distributed cache. This enables session resumption across multiple
 * cluster nodes.
 */
public class CacheBasedDtlsSessionStore implements SessionStore {

    /**
     * Cache key prefix for DTLS session entries.
     */
    public static final String KEY_PREFIX = "coap:session:";

    /**
     * Default time-to-live for stored DTLS sessions (24 hours).
     */
    public static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(24);

    /**
     * Default timeout for retrieving DTLS sessions from the cache (2 seconds).
     */
    public static final Duration DEFAULT_GET_TIMEOUT = Duration.ofSeconds(2);

    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedDtlsSessionStore.class);

    private final Cache<String, String> cache;
    private final CoapAdapterMetrics metrics;
    private final Duration sessionTtl;
    private final Duration getTimeout;

    /**
     * Creates a new session store using default TTL and get timeout, without metrics.
     *
     * @param cache The cache to store sessions in.
     * @throws NullPointerException if cache is {@code null}.
     */
    public CacheBasedDtlsSessionStore(final Cache<String, String> cache) {
        this(cache, null, DEFAULT_SESSION_TTL, DEFAULT_GET_TIMEOUT);
    }

    /**
     * Creates a new session store using default TTL and get timeout.
     *
     * @param cache The cache to store sessions in.
     * @param metrics The metrics collector, or {@code null}.
     * @throws NullPointerException if cache is {@code null}.
     */
    public CacheBasedDtlsSessionStore(final Cache<String, String> cache, final CoapAdapterMetrics metrics) {
        this(cache, metrics, DEFAULT_SESSION_TTL, DEFAULT_GET_TIMEOUT);
    }

    /**
     * Creates a new session store.
     *
     * @param cache The cache to store sessions in.
     * @param metrics The metrics collector, or {@code null}.
     * @param sessionTtl The time-to-live for stored sessions.
     * @param getTimeout The maximum time to wait when retrieving a session.
     * @throws NullPointerException if cache, sessionTtl or getTimeout is {@code null}.
     * @throws IllegalArgumentException if sessionTtl or getTimeout is not positive.
     */
    public CacheBasedDtlsSessionStore(
            final Cache<String, String> cache,
            final CoapAdapterMetrics metrics,
            final Duration sessionTtl,
            final Duration getTimeout) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.metrics = metrics != null ? metrics : CoapAdapterMetrics.NOOP;
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "sessionTtl must not be null");
        this.getTimeout = Objects.requireNonNull(getTimeout, "getTimeout must not be null");
        if (sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalArgumentException("sessionTtl must be positive");
        }
        if (getTimeout.isNegative() || getTimeout.isZero()) {
            throw new IllegalArgumentException("getTimeout must be positive");
        }
    }

    /**
     * Builds the cache key for a session ID.
     *
     * @param sessionId The session ID.
     * @return The cache key.
     * @throws NullPointerException if sessionId is {@code null}.
     */
    public static String toKey(final SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return KEY_PREFIX + sessionId.getAsString();
    }

    /**
     * Gets the configured session time-to-live.
     *
     * @return The session TTL.
     */
    public Duration getSessionTtl() {
        return sessionTtl;
    }

    /**
     * Gets the configured timeout for cache retrieval.
     *
     * @return The get timeout.
     */
    public Duration getGetTimeout() {
        return getTimeout;
    }

    @Override
    public void put(final DTLSSession session) {
        if (session == null) {
            return;
        }
        final SessionId sessionId = session.getSessionIdentifier();
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        try {
            final DatagramWriter writer = new DatagramWriter();
            session.writeTo(writer);
            final byte[] bytes = writer.toByteArray();
            final String base64 = Base64.getEncoder().encodeToString(bytes);
            final String key = toKey(sessionId);
            cache.put(key, base64, sessionTtl.toMillis(), TimeUnit.MILLISECONDS)
                    .onSuccess(v -> LOG.debug("Stored DTLS session in cache [sessionId: {}, ttl: {}ms]",
                            sessionId, sessionTtl.toMillis()))
                    .onFailure(t -> LOG.warn("Failed to store DTLS session in cache [sessionId: {}]",
                            sessionId, t));
        } catch (final Exception e) {
            LOG.warn("Failed to serialize DTLS session for cache [sessionId: {}]", sessionId, e);
        }
    }

    @Override
    public DTLSSession get(final SessionId sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        final String key = toKey(sessionId);
        try {
            final String base64 = cache.get(key)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(getTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (base64 == null) {
                LOG.debug("DTLS session not found in cache [sessionId: {}]", sessionId);
                metrics.incrementResumption(false);
                return null;
            }
            final byte[] bytes = Base64.getDecoder().decode(base64);
            final DatagramReader reader = new DatagramReader(bytes);
            final DTLSSession session = DTLSSession.fromReader(reader);
            if (session == null) {
                LOG.warn("Failed to deserialize DTLS session from cache [sessionId: {}]", sessionId);
                metrics.incrementResumption(false);
                return null;
            }
            LOG.debug("Successfully restored DTLS session from cache [sessionId: {}]", sessionId);
            metrics.incrementResumption(true);
            return session;
        } catch (final TimeoutException e) {
            LOG.warn("Timed out retrieving DTLS session from cache [sessionId: {}, timeout: {}ms]",
                    sessionId, getTimeout.toMillis());
            metrics.incrementResumption(false);
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while retrieving DTLS session from cache [sessionId: {}]", sessionId, e);
            metrics.incrementResumption(false);
            return null;
        } catch (final Exception e) {
            LOG.warn("Failed to retrieve or deserialize DTLS session from cache [sessionId: {}]", sessionId, e);
            metrics.incrementResumption(false);
            return null;
        }
    }

    @Override
    public void remove(final SessionId sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        try {
            final String key = toKey(sessionId);
            cache.get(key)
                    .compose(existingBase64 -> {
                        if (existingBase64 != null) {
                            return cache.remove(key, existingBase64)
                                    .onSuccess(removed -> {
                                        if (removed) {
                                            LOG.debug("Removed DTLS session from cache [sessionId: {}]", sessionId);
                                        } else {
                                            LOG.debug("DTLS session entry was already changed or removed"
                                                    + " [sessionId: {}]", sessionId);
                                        }
                                    })
                                    .mapEmpty();
                        }
                        return Future.succeededFuture();
                    })
                    .onFailure(t -> LOG.warn("Failed to remove DTLS session from cache [sessionId: {}]",
                            sessionId, t));
        } catch (final Exception e) {
            LOG.warn("Failed to initiate removal of DTLS session from cache [sessionId: {}]", sessionId, e);
        }
    }
}
