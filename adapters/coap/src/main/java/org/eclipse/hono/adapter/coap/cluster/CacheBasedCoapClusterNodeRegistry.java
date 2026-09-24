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

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A {@link CoapClusterNodeRegistry} implementation backed by a central {@link Cache}.
 */
public class CacheBasedCoapClusterNodeRegistry implements CoapClusterNodeRegistry {

    /**
     * Key prefix for node entries in the cache.
     */
    public static final String KEY_PREFIX = "coap:node:";

    /**
     * Minimum valid node ID.
     */
    public static final int MIN_NODE_ID = 0;

    /**
     * Maximum valid node ID.
     */
    public static final int MAX_NODE_ID = 255;

    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedCoapClusterNodeRegistry.class);

    private final Cache<String, String> cache;
    private final Clock clock;
    private final Set<Integer> knownNodeIds = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new registry backed by the given cache using the UTC system clock.
     *
     * @param cache The cache to store node registrations in.
     * @throws NullPointerException if cache is {@code null}.
     */
    public CacheBasedCoapClusterNodeRegistry(final Cache<String, String> cache) {
        this(cache, Clock.systemUTC());
    }

    /**
     * Creates a new registry backed by the given cache and clock.
     *
     * @param cache The cache to store node registrations in.
     * @param clock The clock to use for timestamps.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public CacheBasedCoapClusterNodeRegistry(final Cache<String, String> cache, final Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Future<Void> registerNode(final int nodeId, final InetSocketAddress internalAddress, final Duration ttl) {
        Objects.requireNonNull(internalAddress, "internalAddress must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        final String key = toKey(nodeId);
        final String value = toJson(internalAddress, clock.millis());
        knownNodeIds.add(nodeId);

        return cache.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS)
                .onSuccess(v -> LOG.debug("Registered cluster node [nodeId: {}, address: {}, ttl: {}ms]",
                        nodeId, internalAddress, ttl.toMillis()))
                .onFailure(t -> LOG.warn("Failed to register cluster node [nodeId: {}, address: {}]",
                        nodeId, internalAddress, t));
    }

    @Override
    public Future<Void> heartbeat(final int nodeId, final Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        final String key = toKey(nodeId);
        return cache.get(key)
                .compose(existingJson -> {
                    if (existingJson == null) {
                        return Future.failedFuture(new IllegalStateException("Node " + nodeId + " is not registered"));
                    }
                    try {
                        final JsonObject json = new JsonObject(existingJson);
                        json.put("updated", clock.millis());
                        return cache.put(key, json.encode(), ttl.toMillis(), TimeUnit.MILLISECONDS)
                                .onSuccess(v -> LOG.trace("Renewed heartbeat for cluster node [nodeId: {}, ttl: {}ms]",
                                        nodeId, ttl.toMillis()));
                    } catch (final Exception e) {
                        return Future.failedFuture(new IllegalStateException("Corrupt cache entry for node " + nodeId, e));
                    }
                });
    }

    @Override
    public Future<Void> unregisterNode(final int nodeId) {
        final String key = toKey(nodeId);
        knownNodeIds.remove(nodeId);
        return cache.get(key)
                .compose(existingJson -> {
                    if (existingJson != null) {
                        return cache.remove(key, existingJson)
                                .onSuccess(removed -> {
                                    if (removed) {
                                        LOG.debug("Unregistered cluster node [nodeId: {}]", nodeId);
                                    } else {
                                        LOG.debug("Cluster node entry was already changed or removed [nodeId: {}]", nodeId);
                                    }
                                })
                                .mapEmpty();
                    }
                    return Future.succeededFuture();
                });
    }

    @Override
    public Future<InetSocketAddress> getNodeAddress(final int nodeId) {
        final String key = toKey(nodeId);
        return cache.get(key)
                .map(json -> {
                    if (json != null && !json.isBlank()) {
                        return parseAddress(json);
                    }
                    return null;
                });
    }

    @Override
    public Future<Map<Integer, InetSocketAddress>> getAllNodes() {
        final Set<String> keys = new HashSet<>(MAX_NODE_ID - MIN_NODE_ID + 1 + knownNodeIds.size());
        for (int i = MIN_NODE_ID; i <= MAX_NODE_ID; i++) {
            keys.add(toKey(i));
        }
        for (final Integer id : knownNodeIds) {
            keys.add(toKey(id));
        }

        return cache.getAll(keys)
                .map(entries -> {
                    final Map<Integer, InetSocketAddress> result = new HashMap<>();
                    if (entries != null) {
                        entries.forEach((key, json) -> {
                            if (json != null && !json.isBlank()) {
                                try {
                                    final int nodeId = getNodeIdFromKey(key);
                                    final InetSocketAddress address = parseAddress(json);
                                    if (address != null) {
                                        result.put(nodeId, address);
                                    }
                                } catch (final Exception e) {
                                    LOG.warn("Failed to parse cluster node entry for key {}: {}", key, json, e);
                                }
                            }
                        });
                    }
                    return result;
                });
    }

    /**
     * Converts a node ID to its corresponding cache key.
     *
     * @param nodeId The node ID.
     * @return The cache key.
     */
    public static String toKey(final int nodeId) {
        return KEY_PREFIX + nodeId;
    }

    /**
     * Extracts the node ID from a cache key.
     *
     * @param key The cache key.
     * @return The node ID.
     */
    public static int getNodeIdFromKey(final String key) {
        return Integer.parseInt(key.substring(KEY_PREFIX.length()));
    }

    private static String toJson(final InetSocketAddress address, final long timestamp) {
        final String ip = address.getAddress() != null
                ? address.getAddress().getHostAddress()
                : address.getHostString();
        return new JsonObject()
                .put("ip", ip)
                .put("port", address.getPort())
                .put("updated", timestamp)
                .encode();
    }

    private static InetSocketAddress parseAddress(final String jsonString) {
        final JsonObject json = new JsonObject(jsonString);
        final String ip = json.getString("ip");
        final Integer port = json.getInteger("port");
        if (ip != null && port != null) {
            return new InetSocketAddress(ip, port);
        }
        return null;
    }
}
