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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Manages the membership of the local CoAP adapter instance in a cluster of adapter instances.
 * <p>
 * Joining the cluster registers the local adapter instance as a cluster node. It also starts a periodic
 * task that renews the registration and refreshes the view of the other cluster nodes that is used for
 * forwarding DTLS records. Leaving the cluster stops the task and removes the registration.
 * <p>
 * The methods of this class are expected to be invoked on the same Vert.x context.
 */
public final class CoapClusterMembership {

    /**
     * The default maximum time to wait for the registration to be removed when leaving the cluster.
     */
    public static final Duration DEFAULT_LEAVE_TIMEOUT = Duration.ofSeconds(5);

    private static final Logger LOG = LoggerFactory.getLogger(CoapClusterMembership.class);

    private final Vertx vertx;
    private final CoapClusterNodeRegistry registry;
    private final CacheBasedClusterNodesProvider nodesProvider;
    private final int nodeId;
    private final InetSocketAddress address;
    private final Duration nodeTtl;
    private final Duration heartbeatInterval;
    private final Duration leaveTimeout;

    private boolean joined;
    private Long renewalTimerId;
    private Future<Void> pendingRenewal = Future.succeededFuture();

    /**
     * Creates a new membership.
     *
     * @param vertx The Vert.x instance to use for scheduling tasks.
     * @param registry The registry to register the local adapter instance with.
     * @param nodesProvider The provider of the other cluster nodes' addresses to refresh.
     * @param nodeId The cluster node ID of the local adapter instance.
     * @param address The cluster address of the local adapter instance.
     * @param nodeTtl The time-to-live of the registration.
     * @param heartbeatInterval The interval at which the registration is renewed.
     * @param leaveTimeout The maximum time to wait for the registration to be removed when leaving.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the heartbeat interval is shorter than one millisecond or
     *                                  not shorter than the node TTL.
     */
    public CoapClusterMembership(
            final Vertx vertx,
            final CoapClusterNodeRegistry registry,
            final CacheBasedClusterNodesProvider nodesProvider,
            final int nodeId,
            final InetSocketAddress address,
            final Duration nodeTtl,
            final Duration heartbeatInterval,
            final Duration leaveTimeout) {
        this.vertx = Objects.requireNonNull(vertx);
        this.registry = Objects.requireNonNull(registry);
        this.nodesProvider = Objects.requireNonNull(nodesProvider);
        this.nodeId = nodeId;
        this.address = Objects.requireNonNull(address);
        this.nodeTtl = Objects.requireNonNull(nodeTtl);
        this.heartbeatInterval = Objects.requireNonNull(heartbeatInterval);
        this.leaveTimeout = Objects.requireNonNull(leaveTimeout);
        if (heartbeatInterval.toMillis() < 1) {
            throw new IllegalArgumentException("heartbeat interval must be at least one millisecond");
        }
        if (heartbeatInterval.compareTo(nodeTtl) >= 0) {
            throw new IllegalArgumentException("heartbeat interval must be shorter than the node TTL");
        }
    }

    /**
     * Checks if the local adapter instance has joined the cluster and not left it yet.
     *
     * @return {@code true} if the adapter instance is a member of the cluster.
     */
    public boolean isJoined() {
        return joined;
    }

    /**
     * Joins the cluster.
     * <p>
     * If the node ID is registered by another adapter instance, registering is retried until the
     * other registration would have expired if it had not been renewed. This allows the local adapter
     * instance to take over the node ID of an adapter instance that has terminated without removing
     * its registration.
     *
     * @return A succeeded future if the local adapter instance has joined the cluster.
     *         A future failed with a {@link ClusterNodeConflictException} if the node ID is still
     *         registered by another adapter instance. A future failed with another exception if the
     *         local adapter instance could not be registered.
     */
    public Future<Void> join() {
        final long deadline = System.nanoTime() + nodeTtl.plus(heartbeatInterval).toNanos();
        return register(deadline)
                .compose(v -> refreshNodes())
                .onSuccess(v -> {
                    joined = true;
                    renewalTimerId = vertx.setPeriodic(heartbeatInterval.toMillis(), id -> renew());
                    LOG.info("joined cluster [node ID: {}, address: {}]", nodeId, address);
                });
    }

    private Future<Void> register(final long deadline) {
        return registry.registerNode(nodeId, address, nodeTtl)
                .recover(t -> {
                    if (t instanceof ClusterNodeConflictException && System.nanoTime() < deadline) {
                        LOG.warn("{}, retrying until the registration has expired", t.getMessage());
                        final Promise<Void> result = Promise.promise();
                        vertx.setTimer(heartbeatInterval.toMillis(), id -> register(deadline).onComplete(result));
                        return result.future();
                    }
                    return Future.failedFuture(t);
                });
    }

    private Future<Void> refreshNodes() {
        // the provider logs failures itself and keeps its current view of the cluster nodes
        return nodesProvider.refresh().recover(t -> Future.succeededFuture());
    }

    private void renew() {
        if (!joined) {
            return;
        }
        if (!pendingRenewal.isComplete()) {
            LOG.debug("skipping renewal of cluster node registration, previous renewal is still in progress");
            return;
        }
        pendingRenewal = registry.heartbeat(nodeId, nodeTtl)
                .recover(this::handleRenewalFailure)
                .compose(v -> refreshNodes());
    }

    private Future<Void> handleRenewalFailure(final Throwable error) {
        if (!joined) {
            return Future.succeededFuture();
        }
        if (error instanceof ClusterNodeConflictException) {
            LOG.error("""
                    cannot renew cluster node registration, other cluster nodes will not forward \
                    DTLS records for this node: {}\
                    """, error.getMessage());
            return Future.succeededFuture();
        }
        LOG.warn("failed to renew cluster node registration, registering again [node ID: {}]", nodeId, error);
        return registry.registerNode(nodeId, address, nodeTtl)
                .recover(t -> {
                    LOG.error("failed to register cluster node again [node ID: {}]", nodeId, t);
                    return Future.succeededFuture();
                });
    }

    /**
     * Leaves the cluster.
     * <p>
     * Stops renewing the registration and removes it once a renewal that is in progress has completed.
     * Other cluster nodes stop forwarding DTLS records to the local adapter instance once they have
     * refreshed their view of the cluster nodes.
     *
     * @return A future that is succeeded once the registration has been removed, or once removing it has
     *         failed or has not completed within the leave timeout. In the latter cases, the registration
     *         expires after the node TTL.
     */
    public Future<Void> leave() {
        joined = false;
        if (renewalTimerId != null) {
            vertx.cancelTimer(renewalTimerId);
            renewalTimerId = null;
        }
        return pendingRenewal
                .transform(ar -> registry.unregisterNode(nodeId))
                .timeout(leaveTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .onSuccess(v -> LOG.info("left cluster [node ID: {}]", nodeId))
                .recover(t -> {
                    LOG.warn("failed to remove cluster node registration, it will expire after {} [node ID: {}]",
                            nodeTtl, nodeId, t);
                    return Future.succeededFuture();
                });
    }
}
