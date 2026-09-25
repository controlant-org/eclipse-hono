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

/**
 * Indicates that a cluster node ID is already registered by another CoAP adapter instance.
 */
public class ClusterNodeConflictException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final int nodeId;
    private final String ownerHostName;
    private final InetSocketAddress ownerAddress;

    /**
     * Creates a new exception.
     *
     * @param nodeId The conflicting node ID.
     * @param ownerHostName The host name of the adapter instance that has registered the node ID
     *                      or {@code null} if unknown.
     * @param ownerAddress The cluster address of the adapter instance that has registered the node ID
     *                     or {@code null} if unknown.
     */
    public ClusterNodeConflictException(
            final int nodeId,
            final String ownerHostName,
            final InetSocketAddress ownerAddress) {
        super(String.format(
                "cluster node ID %d is registered by another adapter instance [host: %s, address: %s]",
                nodeId, ownerHostName, ownerAddress));
        this.nodeId = nodeId;
        this.ownerHostName = ownerHostName;
        this.ownerAddress = ownerAddress;
    }

    /**
     * Gets the conflicting node ID.
     *
     * @return The node ID.
     */
    public final int getNodeId() {
        return nodeId;
    }

    /**
     * Gets the host name of the adapter instance that has registered the node ID.
     *
     * @return The host name or {@code null} if unknown.
     */
    public final String getOwnerHostName() {
        return ownerHostName;
    }

    /**
     * Gets the cluster address of the adapter instance that has registered the node ID.
     *
     * @return The address or {@code null} if unknown.
     */
    public final InetSocketAddress getOwnerAddress() {
        return ownerAddress;
    }
}
