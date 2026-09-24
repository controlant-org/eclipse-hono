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
import java.time.Duration;
import java.util.Map;

import io.vertx.core.Future;

/**
 * A registry for tracking active CoAP adapter cluster nodes and their internal network addresses.
 */
public interface CoapClusterNodeRegistry {

    /**
     * Registers a cluster node with its internal socket address and time-to-live.
     *
     * @param nodeId The unique cluster node ID (0-255).
     * @param internalAddress The internal socket address for cluster communication.
     * @param ttl The time-to-live duration for this registration.
     * @return A succeeded future if the node was registered successfully, or a failed future on error.
     */
    Future<Void> registerNode(int nodeId, InetSocketAddress internalAddress, Duration ttl);

    /**
     * Renews the heartbeat / lease for an already registered cluster node.
     *
     * @param nodeId The unique cluster node ID.
     * @param ttl The new time-to-live duration.
     * @return A succeeded future if the heartbeat was renewed, or a failed future if the node is not registered.
     */
    Future<Void> heartbeat(int nodeId, Duration ttl);

    /**
     * Unregisters a cluster node.
     *
     * @param nodeId The unique cluster node ID.
     * @return A succeeded future when the unregistration is complete.
     */
    Future<Void> unregisterNode(int nodeId);

    /**
     * Retrieves the internal socket address for a specific cluster node.
     *
     * @param nodeId The cluster node ID.
     * @return A succeeded future containing the socket address or {@code null} if the node is not registered.
     */
    Future<InetSocketAddress> getNodeAddress(int nodeId);

    /**
     * Retrieves all currently active cluster nodes.
     *
     * @return A succeeded future containing a mapping of node IDs to their socket addresses.
     */
    Future<Map<Integer, InetSocketAddress>> getAllNodes();
}

