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
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.californium.scandium.DtlsClusterConnector.ClusterNodesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

/**
 * An implementation of Californium's {@link ClusterNodesProvider} backed by a {@link CoapClusterNodeRegistry}.
 * <p>
 * Maintains an in-memory cache of active cluster node addresses, updated via {@link #refresh()} calls.
 * All lookup operations are non-blocking and strictly in-memory.
 */
public class CacheBasedClusterNodesProvider implements ClusterNodesProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedClusterNodesProvider.class);

    private final CoapClusterNodeRegistry registry;
    private final ConcurrentMap<Integer, InetSocketAddress> nodes = new ConcurrentHashMap<>();

    /**
     * Creates a new provider backed by the given registry.
     *
     * @param registry The cluster node registry.
     * @throws NullPointerException if registry is {@code null}.
     */
    public CacheBasedClusterNodesProvider(final CoapClusterNodeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public InetSocketAddress getClusterNode(final int nodeId) {
        return nodes.get(nodeId);
    }

    @Override
    public boolean available(final InetSocketAddress address) {
        if (address == null) {
            return false;
        }
        for (final InetSocketAddress nodeAddress : nodes.values()) {
            if (matches(nodeAddress, address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queries the registry for all active nodes and updates the local in-memory cache.
     * Nodes no longer present in the registry are removed.
     *
     * @return A succeeded future when refresh completes.
     */
    public Future<Void> refresh() {
        return registry.getAllNodes()
                .map(activeNodes -> {
                    nodes.keySet().removeIf(id -> !activeNodes.containsKey(id));
                    nodes.putAll(activeNodes);
                    return (Void) null;
                })
                .onSuccess(v -> LOG.trace("Refreshed cluster nodes: {} active node(s)", nodes.size()))
                .onFailure(t -> LOG.warn("Failed to refresh cluster nodes provider", t));
    }

    /**
     * Returns an unmodifiable view of currently cached cluster nodes.
     *
     * @return Map of node ID to internal socket address.
     */
    public Map<Integer, InetSocketAddress> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    /**
     * Clears all cached cluster node mappings.
     */
    public void clear() {
        nodes.clear();
    }

    private static boolean matches(final InetSocketAddress a, final InetSocketAddress b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a.getPort() != b.getPort()) {
            return false;
        }
        if (a.getAddress() != null && b.getAddress() != null) {
            return a.getAddress().equals(b.getAddress());
        }
        return Objects.equals(a.getHostString(), b.getHostString());
    }
}
