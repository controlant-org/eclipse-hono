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
 * <p>
 * A registry instance acts on behalf of the adapter instance that runs in the local process.
 * Registrations made by other adapter instances are never renewed or removed by it.
 */
public interface CoapClusterNodeRegistry {

    /**
     * Registers the local adapter instance as a cluster node with its internal socket address and time-to-live.
     * <p>
     * A registration of the same node ID by a previous incarnation of the local adapter instance,
     * i.e. by an adapter instance that ran on the same host, is replaced.
     *
     * @param nodeId The unique cluster node ID (0-255).
     * @param internalAddress The internal socket address for cluster communication.
     * @param ttl The time-to-live duration for this registration.
     * @return A succeeded future if the node was registered successfully.
     *         A future failed with a {@link ClusterNodeConflictException} if the node ID is registered
     *         by another adapter instance. A future failed with another exception on error.
     */
    Future<Void> registerNode(int nodeId, InetSocketAddress internalAddress, Duration ttl);

    /**
     * Renews the registration of the local adapter instance as a cluster node.
     *
     * @param nodeId The unique cluster node ID.
     * @param ttl The new time-to-live duration.
     * @return A succeeded future if the registration was renewed.
     *         A future failed with a {@link ClusterNodeConflictException} if the node ID is registered
     *         by another adapter instance. A future failed with another exception if the node is not
     *         registered or on error.
     */
    Future<Void> heartbeat(int nodeId, Duration ttl);

    /**
     * Removes the registration of the local adapter instance as a cluster node.
     * <p>
     * The registration is only removed if it is still the one that has been made by the
     * local adapter instance.
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

