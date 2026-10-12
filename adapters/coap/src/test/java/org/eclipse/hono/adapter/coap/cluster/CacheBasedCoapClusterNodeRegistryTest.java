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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

/**
 * Tests verifying that {@link CacheBasedCoapClusterNodeRegistry} only renews and removes
 * registrations of the local adapter instance.
 */
public class CacheBasedCoapClusterNodeRegistryTest {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final InetSocketAddress ADDRESS_A = new InetSocketAddress("10.0.0.1", 5685);
    private static final InetSocketAddress ADDRESS_B = new InetSocketAddress("10.0.0.2", 5685);

    private TestClock clock;
    private InMemoryTestCache cache;
    private CacheBasedCoapClusterNodeRegistry registryA;
    private CacheBasedCoapClusterNodeRegistry registryB;

    /**
     * Sets up two registries acting on behalf of adapter instances on different hosts.
     */
    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-09-24T00:00:00Z"));
        cache = new InMemoryTestCache(clock);
        registryA = new CacheBasedCoapClusterNodeRegistry(cache, clock, "instance-a", "coap-adapter-0");
        registryB = new CacheBasedCoapClusterNodeRegistry(cache, clock, "instance-b", "coap-adapter-1");
    }

    private static <T> T await(final Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private static Throwable awaitFailure(final Future<?> future) {
        try {
            await(future);
        } catch (final CompletionException e) {
            return e.getCause();
        }
        throw new AssertionError("future should have failed");
    }

    /**
     * Verifies that a node ID that is registered by an adapter instance on another host cannot be registered.
     */
    @Test
    void testRegisterNodeFailsForNodeIdOfOtherHost() {
        await(registryA.registerNode(3, ADDRESS_A, TTL));

        final Throwable t = awaitFailure(registryB.registerNode(3, ADDRESS_B, TTL));

        final ClusterNodeConflictException conflict = assertInstanceOf(ClusterNodeConflictException.class, t);
        assertEquals(3, conflict.getNodeId());
        assertEquals("coap-adapter-0", conflict.getOwnerHostName());
        assertEquals(ADDRESS_A, conflict.getOwnerAddress());
        assertEquals(ADDRESS_A, await(registryA.getNodeAddress(3)));
    }

    /**
     * Verifies that a node ID can be registered once the registration of another adapter instance has expired.
     */
    @Test
    void testRegisterNodeSucceedsAfterRegistrationOfOtherHostExpired() {
        await(registryA.registerNode(3, ADDRESS_A, TTL));
        clock.advance(TTL.plusSeconds(1));

        await(registryB.registerNode(3, ADDRESS_B, TTL));

        assertEquals(ADDRESS_B, await(registryB.getNodeAddress(3)));
    }

    /**
     * Verifies that a registration of a previous adapter instance on the same host is replaced,
     * e.g. after the container of a Kubernetes pod has been restarted.
     */
    @Test
    void testRegisterNodeReplacesRegistrationOfPreviousInstanceOnSameHost() {
        await(registryA.registerNode(3, ADDRESS_A, TTL));
        final InetSocketAddress newAddress = new InetSocketAddress("10.0.0.9", 5685);
        final CacheBasedCoapClusterNodeRegistry restartedA = new CacheBasedCoapClusterNodeRegistry(
                cache, clock, "instance-a-restarted", "coap-adapter-0");

        await(restartedA.registerNode(3, newAddress, TTL));

        assertEquals(newAddress, await(restartedA.getNodeAddress(3)));
        // the previous instance can no longer renew the registration
        assertInstanceOf(ClusterNodeConflictException.class, awaitFailure(registryA.heartbeat(3, TTL)));
    }

    /**
     * Verifies that an adapter instance can register its own node ID again,
     * e.g. after renewing the registration has failed.
     */
    @Test
    void testRegisterNodeSucceedsForOwnRegistration() {
        await(registryA.registerNode(3, ADDRESS_A, TTL));

        await(registryA.registerNode(3, ADDRESS_A, TTL));

        assertEquals(ADDRESS_A, await(registryA.getNodeAddress(3)));
    }

    /**
     * Verifies that an adapter instance does not renew a registration of another adapter instance.
     */
    @Test
    void testHeartbeatFailsForRegistrationOfOtherInstance() {
        await(registryB.registerNode(3, ADDRESS_B, TTL));

        final Throwable t = awaitFailure(registryA.heartbeat(3, TTL));

        assertInstanceOf(ClusterNodeConflictException.class, t);
        assertEquals(ADDRESS_B, await(registryA.getNodeAddress(3)));
    }

    /**
     * Verifies that an adapter instance does not remove a registration of another adapter instance
     * that has replaced its own registration.
     */
    @Test
    void testUnregisterNodeDoesNotRemoveRegistrationOfOtherInstance() {
        await(registryA.registerNode(3, ADDRESS_A, TTL));
        clock.advance(TTL.plusSeconds(1));
        await(registryB.registerNode(3, ADDRESS_B, TTL));

        await(registryA.unregisterNode(3));

        assertEquals(ADDRESS_B, await(registryB.getNodeAddress(3)));
    }

    /**
     * Verifies that an adapter instance removes its own registration after it has been renewed.
     */
    @Test
    void testUnregisterNodeRemovesRenewedOwnRegistration() {
        await(registryA.registerNode(3, ADDRESS_A, TTL));
        clock.advance(Duration.ofSeconds(10));
        await(registryA.heartbeat(3, TTL));

        await(registryA.unregisterNode(3));

        assertNull(await(registryA.getNodeAddress(3)));
    }

    /**
     * Verifies that unregistering a node ID that has never been registered succeeds.
     */
    @Test
    void testUnregisterNodeSucceedsForUnknownNode() {
        await(registryB.registerNode(3, ADDRESS_B, TTL));

        assertTrue(registryA.unregisterNode(3).succeeded());
        assertEquals(ADDRESS_B, await(registryB.getNodeAddress(3)));
    }
}
