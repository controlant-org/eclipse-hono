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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link CoapClusterMembership}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class CoapClusterMembershipTest {

    private static final int NODE_ID = 3;
    private static final InetSocketAddress ADDRESS_A = new InetSocketAddress("10.0.0.1", 5685);
    private static final InetSocketAddress ADDRESS_B = new InetSocketAddress("10.0.0.2", 5685);
    private static final Duration HEARTBEAT = Duration.ofMillis(20);

    private TestClock clock;
    private InMemoryTestCache cache;
    private CacheBasedCoapClusterNodeRegistry registryA;
    private CacheBasedCoapClusterNodeRegistry registryB;
    private CacheBasedClusterNodesProvider provider;

    /**
     * Sets up two registries acting on behalf of adapter instances on different hosts.
     */
    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-09-24T00:00:00Z"));
        cache = new InMemoryTestCache(clock);
        registryA = new CacheBasedCoapClusterNodeRegistry(cache, clock, "instance-a", "coap-adapter-0");
        registryB = new CacheBasedCoapClusterNodeRegistry(cache, clock, "instance-b", "coap-adapter-1");
        provider = new CacheBasedClusterNodesProvider(registryA);
    }

    private CoapClusterMembership newMembership(
            final Vertx vertx,
            final CoapClusterNodeRegistry registry,
            final Duration nodeTtl,
            final Duration leaveTimeout) {
        return new CoapClusterMembership(
                vertx, registry, provider, NODE_ID, ADDRESS_A, nodeTtl, HEARTBEAT, leaveTimeout);
    }

    /**
     * Verifies that a membership cannot be created with a heartbeat interval that is not shorter
     * than the node TTL.
     *
     * @param vertx The Vert.x instance.
     */
    @Test
    void testConstructorRejectsHeartbeatNotShorterThanNodeTtl(final Vertx vertx) {
        assertThrows(IllegalArgumentException.class, () -> newMembership(
                vertx, registryA, HEARTBEAT, CoapClusterMembership.DEFAULT_LEAVE_TIMEOUT));
    }

    /**
     * Verifies that joining registers the node, refreshes the view of the cluster nodes
     * and periodically renews the registration.
     *
     * @param vertx The Vert.x instance.
     * @param ctx The test context.
     */
    @Test
    void testJoinRegistersNodeAndRenewsRegistration(final Vertx vertx, final VertxTestContext ctx) {
        final CoapClusterNodeRegistry registry = mock(CoapClusterNodeRegistry.class);
        when(registry.registerNode(anyInt(), any(), any())).thenReturn(Future.succeededFuture());
        when(registry.heartbeat(anyInt(), any())).thenReturn(Future.succeededFuture());
        when(registry.unregisterNode(anyInt())).thenReturn(Future.succeededFuture());
        final CoapClusterMembership membership = newMembership(
                vertx, registry, Duration.ofSeconds(30), CoapClusterMembership.DEFAULT_LEAVE_TIMEOUT);

        membership.join()
                .compose(v -> {
                    ctx.verify(() -> {
                        verify(registry).registerNode(NODE_ID, ADDRESS_A, Duration.ofSeconds(30));
                        assertThat(membership.isJoined()).isTrue();
                    });
                    final Promise<Void> result = Promise.promise();
                    vertx.setTimer(100, id -> result.complete());
                    return result.future();
                })
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> verify(registry, atLeastOnce()).heartbeat(NODE_ID, Duration.ofSeconds(30)));
                    membership.leave();
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that joining waits for a registration of the node ID by another adapter instance
     * to expire, e.g. if that adapter instance has terminated without unregistering.
     *
     * @param vertx The Vert.x instance.
     * @param ctx The test context.
     */
    @Test
    void testJoinWaitsForConflictingRegistrationToExpire(final Vertx vertx, final VertxTestContext ctx) {
        final Duration nodeTtl = Duration.ofMillis(500);
        registryB.registerNode(NODE_ID, ADDRESS_B, nodeTtl);
        final CoapClusterMembership membership = newMembership(
                vertx, registryA, nodeTtl, CoapClusterMembership.DEFAULT_LEAVE_TIMEOUT);
        // let the other registration expire while the local instance is waiting
        vertx.setTimer(100, id -> clock.advance(nodeTtl));

        membership.join()
                .compose(v -> registryA.getNodeAddress(NODE_ID))
                .onComplete(ctx.succeeding(address -> {
                    ctx.verify(() -> {
                        assertThat(address).isEqualTo(ADDRESS_A);
                        assertThat(membership.isJoined()).isTrue();
                    });
                    membership.leave();
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that joining fails if the node ID is still registered by another adapter instance
     * after the registration would have expired if it had not been renewed.
     *
     * @param vertx The Vert.x instance.
     * @param ctx The test context.
     */
    @Test
    void testJoinFailsForLiveConflictingRegistration(final Vertx vertx, final VertxTestContext ctx) {
        final Duration nodeTtl = Duration.ofMillis(100);
        registryB.registerNode(NODE_ID, ADDRESS_B, nodeTtl);
        final CoapClusterMembership membership = newMembership(
                vertx, registryA, nodeTtl, CoapClusterMembership.DEFAULT_LEAVE_TIMEOUT);

        membership.join()
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ClusterNodeConflictException.class);
                        assertThat(membership.isJoined()).isFalse();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that an adapter instance does not overwrite the registration of another adapter instance
     * that has taken over its node ID while it was running.
     *
     * @param vertx The Vert.x instance.
     * @param ctx The test context.
     */
    @Test
    void testRenewalDoesNotOverwriteRegistrationOfOtherInstance(final Vertx vertx, final VertxTestContext ctx) {
        final CoapClusterMembership membership = newMembership(
                vertx, registryA, Duration.ofSeconds(30), CoapClusterMembership.DEFAULT_LEAVE_TIMEOUT);
        final InetSocketAddress newAddress = new InetSocketAddress("10.0.0.9", 5685);
        final CacheBasedCoapClusterNodeRegistry restartedA = new CacheBasedCoapClusterNodeRegistry(
                cache, clock, "instance-a-restarted", "coap-adapter-0");

        membership.join()
                .compose(v -> restartedA.registerNode(NODE_ID, newAddress, Duration.ofSeconds(30)))
                .compose(v -> {
                    final Promise<Void> result = Promise.promise();
                    vertx.setTimer(100, id -> result.complete());
                    return result.future();
                })
                .compose(v -> restartedA.getNodeAddress(NODE_ID))
                .onComplete(ctx.succeeding(address -> {
                    ctx.verify(() -> assertThat(address).isEqualTo(newAddress));
                    membership.leave();
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that leaving the cluster completes within the leave timeout if removing the
     * registration does not complete.
     *
     * @param vertx The Vert.x instance.
     * @param ctx The test context.
     */
    @Test
    void testLeaveCompletesWhenUnregisteringDoesNotComplete(final Vertx vertx, final VertxTestContext ctx) {
        final CoapClusterNodeRegistry registry = mock(CoapClusterNodeRegistry.class);
        when(registry.registerNode(anyInt(), any(), any())).thenReturn(Future.succeededFuture());
        when(registry.heartbeat(anyInt(), any())).thenReturn(Future.succeededFuture());
        when(registry.unregisterNode(anyInt())).thenReturn(Promise.<Void>promise().future());
        final CoapClusterMembership membership = newMembership(
                vertx, registry, Duration.ofSeconds(30), Duration.ofMillis(100));

        membership.join()
                .compose(v -> membership.leave())
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(registry).unregisterNode(NODE_ID);
                        assertThat(membership.isJoined()).isFalse();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a renewal that is in progress when leaving the cluster does not register
     * the node again, and that the registration is removed once the renewal has completed.
     *
     * @param vertx The Vert.x instance.
     * @param ctx The test context.
     */
    @Test
    void testLeaveWaitsForPendingRenewal(final Vertx vertx, final VertxTestContext ctx) {
        final CoapClusterNodeRegistry registry = mock(CoapClusterNodeRegistry.class);
        final Promise<Void> pendingHeartbeat = Promise.promise();
        when(registry.registerNode(anyInt(), any(), any())).thenReturn(Future.succeededFuture());
        when(registry.heartbeat(anyInt(), any())).thenReturn(pendingHeartbeat.future());
        when(registry.unregisterNode(anyInt())).thenReturn(Future.succeededFuture());
        final CoapClusterMembership membership = newMembership(
                vertx, registry, Duration.ofSeconds(30), CoapClusterMembership.DEFAULT_LEAVE_TIMEOUT);
        final Context context = vertx.getOrCreateContext();

        context.runOnContext(go -> {
            membership.join()
                    .compose(v -> {
                        final Promise<Void> result = Promise.promise();
                        vertx.setTimer(HEARTBEAT.multipliedBy(3).toMillis(), id -> result.complete());
                        return result.future();
                    })
                    .compose(v -> {
                        ctx.verify(() -> verify(registry).heartbeat(NODE_ID, Duration.ofSeconds(30)));
                        final Future<Void> left = membership.leave();
                        ctx.verify(() -> verify(registry, times(0)).unregisterNode(anyInt()));
                        // the registration has expired while the renewal was in progress
                        pendingHeartbeat.fail(new IllegalStateException("Node 3 is not registered"));
                        return left;
                    })
                    .onComplete(ctx.succeeding(v -> {
                        ctx.verify(() -> {
                            verify(registry, times(1)).registerNode(anyInt(), any(), any());
                            verify(registry).unregisterNode(NODE_ID);
                        });
                        ctx.completeNow();
                    }));
        });
    }
}
