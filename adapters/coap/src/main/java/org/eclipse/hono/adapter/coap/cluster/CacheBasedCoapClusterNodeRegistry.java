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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A {@link CoapClusterNodeRegistry} implementation backed by a central {@link Cache}.
 * <p>
 * Each registration contains the cluster address of the adapter instance that has made it,
 * the name of the host that the adapter instance runs on and an identifier of the adapter
 * instance that is unique for the lifetime of the process. This information is used to prevent
 * an adapter instance from renewing or removing a registration of another adapter instance.
 */
public class CacheBasedCoapClusterNodeRegistry implements CoapClusterNodeRegistry {

    /**
     * Key prefix for node entries in the cache.
     * <p>
     * The prefix is a Redis hash tag, which makes all node entries map to the same hash slot of a
     * Redis Cluster. The entries of all nodes can therefore be read with a single {@code MGET}
     * command, which a Redis Cluster client would otherwise split by hash slot.
     */
    public static final String KEY_PREFIX = "{coap:node}:";

    /**
     * Minimum valid node ID.
     */
    public static final int MIN_NODE_ID = 0;

    /**
     * Maximum valid node ID.
     */
    public static final int MAX_NODE_ID = 255;

    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedCoapClusterNodeRegistry.class);

    private static final String FIELD_IP = "ip";
    private static final String FIELD_PORT = "port";
    private static final String FIELD_HOST = "host";
    private static final String FIELD_INSTANCE = "instance";
    private static final String FIELD_UPDATED = "updated";

    private final Cache<String, String> cache;
    private final Clock clock;
    private final String instanceId;
    private final String hostName;
    private final ConcurrentMap<Integer, String> ownEntries = new ConcurrentHashMap<>();

    /**
     * Creates a new registry backed by the given cache using the UTC system clock.
     * <p>
     * The local host name is determined by means of {@link ClusterNodeIdentity#localHostName()}.
     *
     * @param cache The cache to store node registrations in.
     * @throws NullPointerException if cache is {@code null}.
     */
    public CacheBasedCoapClusterNodeRegistry(final Cache<String, String> cache) {
        this(cache, Clock.systemUTC());
    }

    /**
     * Creates a new registry backed by the given cache and clock.
     * <p>
     * The local host name is determined by means of {@link ClusterNodeIdentity#localHostName()}.
     *
     * @param cache The cache to store node registrations in.
     * @param clock The clock to use for timestamps.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public CacheBasedCoapClusterNodeRegistry(final Cache<String, String> cache, final Clock clock) {
        this(cache, clock, UUID.randomUUID().toString(), ClusterNodeIdentity.localHostName().orElse(null));
    }

    /**
     * Creates a new registry backed by the given cache and clock.
     *
     * @param cache The cache to store node registrations in.
     * @param clock The clock to use for timestamps.
     * @param instanceId The identifier of the local adapter instance.
     * @param hostName The name of the host that the local adapter instance runs on or {@code null} if unknown.
     * @throws NullPointerException if any parameter other than host name is {@code null}.
     */
    public CacheBasedCoapClusterNodeRegistry(
            final Cache<String, String> cache,
            final Clock clock,
            final String instanceId,
            final String hostName) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
        this.hostName = hostName;
    }

    @Override
    public Future<Void> registerNode(final int nodeId, final InetSocketAddress internalAddress, final Duration ttl) {
        checkNodeId(nodeId);
        Objects.requireNonNull(internalAddress, "internalAddress must not be null");
        checkTtl(ttl);

        final String key = toKey(nodeId);
        final String value = toJson(internalAddress);

        return cache.putIfAbsent(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS)
                .compose(stored -> stored ? Future.succeededFuture() : replaceOwnRegistration(nodeId, value, ttl))
                .onSuccess(v -> {
                    ownEntries.put(nodeId, value);
                    LOG.debug("Registered cluster node [nodeId: {}, address: {}, ttl: {}ms]",
                            nodeId, internalAddress, ttl.toMillis());
                })
                .onFailure(t -> LOG.debug("Failed to register cluster node [nodeId: {}, address: {}]",
                        nodeId, internalAddress, t));
    }

    private Future<Void> replaceOwnRegistration(final int nodeId, final String value, final Duration ttl) {
        final String key = toKey(nodeId);
        return cache.get(key)
                .compose(existingJson -> {
                    if (existingJson == null) {
                        // the existing registration has expired in the meantime
                        return cache.putIfAbsent(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS)
                                .compose(stored -> stored
                                        ? Future.succeededFuture()
                                        : Future.failedFuture(new ClusterNodeConflictException(nodeId, null, null)));
                    }
                    final JsonObject existing;
                    try {
                        existing = new JsonObject(existingJson);
                    } catch (final Exception e) {
                        LOG.warn("Replacing corrupt cache entry for cluster node [nodeId: {}]", nodeId, e);
                        return cache.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    if (isOwnRegistration(existing)) {
                        return cache.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    if (hostName != null && hostName.equals(existing.getString(FIELD_HOST))) {
                        LOG.info("Replacing registration of previous adapter instance on host {} [nodeId: {}]",
                                hostName, nodeId);
                        return cache.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    return Future.failedFuture(new ClusterNodeConflictException(
                            nodeId,
                            existing.getString(FIELD_HOST),
                            parseAddress(existing)));
                });
    }

    @Override
    public Future<Void> heartbeat(final int nodeId, final Duration ttl) {
        checkNodeId(nodeId);
        checkTtl(ttl);

        final String key = toKey(nodeId);
        return cache.get(key)
                .compose(existingJson -> {
                    if (existingJson == null) {
                        return Future.failedFuture(new IllegalStateException("Node " + nodeId + " is not registered"));
                    }
                    final JsonObject existing;
                    try {
                        existing = new JsonObject(existingJson);
                    } catch (final Exception e) {
                        return Future.failedFuture(new IllegalStateException("Corrupt cache entry for node " + nodeId, e));
                    }
                    if (!isOwnRegistration(existing)) {
                        return Future.failedFuture(new ClusterNodeConflictException(
                                nodeId,
                                existing.getString(FIELD_HOST),
                                parseAddress(existing)));
                    }
                    final String value = toJson(parseAddress(existing));
                    return cache.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS)
                            .onSuccess(v -> {
                                ownEntries.put(nodeId, value);
                                LOG.trace("Renewed heartbeat for cluster node [nodeId: {}, ttl: {}ms]",
                                        nodeId, ttl.toMillis());
                            });
                });
    }

    @Override
    public Future<Void> unregisterNode(final int nodeId) {
        checkNodeId(nodeId);
        final String ownEntry = ownEntries.remove(nodeId);
        if (ownEntry == null) {
            return Future.succeededFuture();
        }
        return cache.remove(toKey(nodeId), ownEntry)
                .onSuccess(removed -> {
                    if (removed) {
                        LOG.debug("Unregistered cluster node [nodeId: {}]", nodeId);
                    } else {
                        LOG.debug("Cluster node entry was already changed or removed [nodeId: {}]", nodeId);
                    }
                })
                .mapEmpty();
    }

    @Override
    public Future<InetSocketAddress> getNodeAddress(final int nodeId) {
        checkNodeId(nodeId);
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
        final Set<String> keys = new HashSet<>(MAX_NODE_ID - MIN_NODE_ID + 1);
        for (int i = MIN_NODE_ID; i <= MAX_NODE_ID; i++) {
            keys.add(toKey(i));
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
     * @throws IllegalArgumentException if nodeId is out of range [0..255].
     */
    public static String toKey(final int nodeId) {
        checkNodeId(nodeId);
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

    private static void checkNodeId(final int nodeId) {
        if (nodeId < MIN_NODE_ID || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                    String.format("nodeId %d out of bounds [%d..%d]", nodeId, MIN_NODE_ID, MAX_NODE_ID));
        }
    }

    private static void checkTtl(final Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    private boolean isOwnRegistration(final JsonObject entry) {
        return instanceId.equals(entry.getString(FIELD_INSTANCE));
    }

    private String toJson(final InetSocketAddress address) {
        final String ip = address.getAddress() != null
                ? address.getAddress().getHostAddress()
                : address.getHostString();
        final JsonObject json = new JsonObject()
                .put(FIELD_IP, ip)
                .put(FIELD_PORT, address.getPort())
                .put(FIELD_INSTANCE, instanceId)
                .put(FIELD_UPDATED, clock.millis());
        if (hostName != null) {
            json.put(FIELD_HOST, hostName);
        }
        return json.encode();
    }

    private static InetSocketAddress parseAddress(final String jsonString) {
        return parseAddress(new JsonObject(jsonString));
    }

    private static InetSocketAddress parseAddress(final JsonObject json) {
        final String ip = json.getString(FIELD_IP);
        final Integer port = json.getInteger(FIELD_PORT);
        if (ip != null && port != null) {
            return new InetSocketAddress(ip, port);
        }
        return null;
    }
}
