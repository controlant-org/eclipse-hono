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

package org.eclipse.hono.adapter.coap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.DtlsClusterConnector;
import org.eclipse.californium.scandium.config.DtlsClusterConnectorConfig;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.Connection;
import org.eclipse.californium.scandium.dtls.ConnectionId;
import org.eclipse.californium.scandium.dtls.ContentType;
import org.eclipse.californium.scandium.dtls.MultiNodeConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.ResumptionSupportingConnectionStore;
import org.eclipse.californium.scandium.dtls.SingleNodeConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedSinglePskStore;
import org.eclipse.hono.adapter.coap.cluster.CacheBasedClusterNodesProvider;
import org.eclipse.hono.adapter.coap.cluster.CacheBasedCoapClusterNodeRegistry;
import org.eclipse.hono.deviceconnection.common.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Integration tests verifying DTLS Connection ID (CID) cluster forwarding,
 * and NAT rebinding handling across cluster nodes.
 */
public class CoapClusterIntegrationTest {

    private static final String PSK_IDENTITY = "sensor1";
    private static final byte[] PSK_KEY = "secret-key".getBytes(StandardCharsets.UTF_8);
    private static final int CID_LENGTH = 6;

    private InMemoryTestCache sharedCache;
    private CacheBasedCoapClusterNodeRegistry registry;
    private CacheBasedClusterNodesProvider provider0;
    private CacheBasedClusterNodesProvider provider1;

    private CoapServer server0;
    private CoapServer server1;
    private DtlsClusterConnector connector0;
    private DtlsClusterConnector connector1;

    private final List<CoapClient> clientsToCleanUp = new ArrayList<>();
    private final List<CoapEndpoint> endpointsToCleanUp = new ArrayList<>();

    /**
     * Initializes test fixtures before each test execution.
     */
    @BeforeEach
    void setUp() {
        final TestClock clock = new TestClock(Instant.now());
        sharedCache = new InMemoryTestCache(clock);
        registry = new CacheBasedCoapClusterNodeRegistry(sharedCache, clock);
        provider0 = new CacheBasedClusterNodesProvider(registry);
        provider1 = new CacheBasedClusterNodesProvider(registry);
    }

    /**
     * Cleans up all running servers, endpoints, and clients after each test.
     */
    @AfterEach
    void tearDown() {
        for (final CoapClient client : clientsToCleanUp) {
            try {
                client.shutdown();
            } catch (final Exception ignored) {
            }
        }
        clientsToCleanUp.clear();

        for (final CoapEndpoint endpoint : endpointsToCleanUp) {
            try {
                endpoint.destroy();
            } catch (final Exception ignored) {
            }
        }
        endpointsToCleanUp.clear();

        if (server0 != null) {
            try {
                server0.destroy();
            } catch (final Exception ignored) {
            }
            server0 = null;
        }

        if (server1 != null) {
            try {
                server1.destroy();
            } catch (final Exception ignored) {
            }
            server1 = null;
        }
    }

    private static ResumptionSupportingConnectionStore getConnectionStore(final DTLSConnector connector) {
        try {
            final Field field = DTLSConnector.class.getDeclaredField("connectionStore");
            field.setAccessible(true);
            return (ResumptionSupportingConnectionStore) field.get(connector);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to access connectionStore via reflection", e);
        }
    }

    private CoapServer createServerNode(
            final int nodeId,
            final CacheBasedClusterNodesProvider provider) {

        final Configuration config = Configuration.createStandardWithoutFile();
        final DtlsConnectorConfig.Builder dtlsBuilder = DtlsConnectorConfig.builder(config)
                .setAddress(new InetSocketAddress("127.0.0.1", 0))
                .setAdvancedPskStore(new AdvancedSinglePskStore(PSK_IDENTITY, PSK_KEY))
                .setConnectionIdGenerator(new MultiNodeConnectionIdGenerator(nodeId, CID_LENGTH))
                .set(DtlsConfig.DTLS_CONNECTION_ID_LENGTH, CID_LENGTH);

        final DtlsClusterConnectorConfig.Builder clusterConfigBuilder = DtlsClusterConnectorConfig.builder();
        clusterConfigBuilder.setAddress(new InetSocketAddress("127.0.0.1", 0));

        final DtlsClusterConnector connector = new DtlsClusterConnector(
                dtlsBuilder.build(),
                clusterConfigBuilder.build(),
                provider);

        if (nodeId == 0) {
            connector0 = connector;
        } else {
            connector1 = connector;
        }

        final CoapEndpoint endpoint = CoapEndpoint.builder()
                .setConfiguration(config)
                .setConnector(connector)
                .build();

        final CoapServer server = new CoapServer(config);
        server.addEndpoint(endpoint);
        server.add(new CoapResource("telemetry") {
            @Override
            public void handlePOST(final CoapExchange exchange) {
                exchange.respond(ResponseCode.CHANGED);
            }
        });

        server.start();
        return server;
    }

    /**
     * Verifies NAT rebinding and cluster mesh packet forwarding across nodes.
     *
     * @throws Exception if an error occurs.
     */
    @Test
    void testNatRebindingClusterForwarding() throws Exception {
        server0 = createServerNode(0, provider0);
        server1 = createServerNode(1, provider1);

        final InetSocketAddress clusterAddr0 = connector0.getClusterInternalAddress();
        final InetSocketAddress clusterAddr1 = connector1.getClusterInternalAddress();

        registry.registerNode(0, clusterAddr0, Duration.ofSeconds(60)).toCompletionStage().toCompletableFuture().join();
        registry.registerNode(1, clusterAddr1, Duration.ofSeconds(60)).toCompletionStage().toCompletableFuture().join();

        provider0.refresh().toCompletionStage().toCompletableFuture().join();
        provider1.refresh().toCompletionStage().toCompletableFuture().join();

        assertEquals(clusterAddr0, provider1.getClusterNode(0));
        assertEquals(clusterAddr1, provider0.getClusterNode(1));

        final Configuration clientConfig = Configuration.createStandardWithoutFile();
        final DtlsConnectorConfig.Builder clientDtlsBuilder = DtlsConnectorConfig.builder(clientConfig)
                .setAddress(new InetSocketAddress("127.0.0.1", 0))
                .setAdvancedPskStore(new AdvancedSinglePskStore(PSK_IDENTITY, PSK_KEY))
                .setConnectionIdGenerator(new SingleNodeConnectionIdGenerator(CID_LENGTH))
                .set(DtlsConfig.DTLS_CONNECTION_ID_LENGTH, CID_LENGTH);

        final DTLSConnector clientConnector = new DTLSConnector(clientDtlsBuilder.build());
        final CoapEndpoint clientEndpoint = CoapEndpoint.builder()
                .setConfiguration(clientConfig)
                .setConnector(clientConnector)
                .build();
        clientEndpoint.start();
        endpointsToCleanUp.add(clientEndpoint);

        final InetSocketAddress node0PublicAddr = connector0.getAddress();
        final InetSocketAddress node1PublicAddr = connector1.getAddress();

        final CoapClient client = new CoapClient("coaps://127.0.0.1:" + node0PublicAddr.getPort() + "/telemetry");
        client.setEndpoint(clientEndpoint);
        clientsToCleanUp.add(client);

        // 1. Initial request to Node 0
        final CoapResponse response1 = client.post("initial-payload", MediaTypeRegistry.TEXT_PLAIN);
        assertNotNull(response1, "First CoAP response should not be null");
        assertEquals(ResponseCode.CHANGED, response1.getCode());

        // Verify connection established with CID
        final ResumptionSupportingConnectionStore clientStore = getConnectionStore(clientConnector);
        final Connection connection = clientStore.get(node0PublicAddr);
        assertNotNull(connection, "Client connection should exist for Node 0");
        assertNotNull(connection.getEstablishedDtlsContext(), "Connection should have established DTLS context");
        final ConnectionId serverCid = connection.getEstablishedDtlsContext().getWriteConnectionId();
        assertNotNull(serverCid, "Server CID should be set in writeConnectionId");
        assertEquals((byte) 0, serverCid.getBytes()[0], "Server CID prefix should match Node 0 ID");

        // 2. Simulate NAT rebinding: traffic with Node 0's CID lands on Node 1's public port
        clientStore.update(connection, node1PublicAddr);
        client.setURI("coaps://127.0.0.1:" + node1PublicAddr.getPort() + "/telemetry");

        final CoapResponse response2 = client.post("rebound-payload", MediaTypeRegistry.TEXT_PLAIN);
        assertNotNull(response2, "Rebound CoAP response forwarded through Node 1 to Node 0 should not be null");
        assertEquals(ResponseCode.CHANGED, response2.getCode());
    }

    /**
     * Verifies that a foreign packet arriving for an unknown or offline node is dropped gracefully without crashing.
     *
     * @throws Exception if an error occurs.
     */
    @Test
    void testClusterForwardDroppedWhenNodeOffline() throws Exception {
        server1 = createServerNode(1, provider1);
        provider1.refresh().toCompletionStage().toCompletableFuture().join();

        final InetSocketAddress node1PublicAddr = connector1.getAddress();

        // Construct a DTLS CID record with foreign nodeId = 99 (unknown/offline node)
        // Record structure: ContentType.TLS12_CID (1 byte) + Version (2 bytes) + Epoch (2 bytes) + Seq (6 bytes)
        //                   + CID (6 bytes: byte 0 = 99) + Length (2 bytes) + Payload (4 bytes)
        final byte[] packetData = new byte[] {
            (byte) ContentType.TLS12_CID.getCode(),
            (byte) 0xfe, (byte) 0xfd,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
            (byte) 99, 0x01, 0x02, 0x03, 0x04, 0x05,
            0x00, 0x04,
            0x00, 0x00, 0x00, 0x00
        };

        try (DatagramSocket rawSocket = new DatagramSocket()) {
            final DatagramPacket datagram = new DatagramPacket(
                    packetData,
                    packetData.length,
                    node1PublicAddr);
            rawSocket.send(datagram);
        }

        // Small wait to ensure packet is processed and dropped by worker thread
        Thread.sleep(100);

        // Verify Node 1 is completely healthy and can still process legitimate CoAP requests
        final Configuration clientConfig = Configuration.createStandardWithoutFile();
        final DtlsConnectorConfig.Builder clientDtlsBuilder = DtlsConnectorConfig.builder(clientConfig)
                .setAddress(new InetSocketAddress("127.0.0.1", 0))
                .setAdvancedPskStore(new AdvancedSinglePskStore(PSK_IDENTITY, PSK_KEY))
                .setConnectionIdGenerator(new SingleNodeConnectionIdGenerator(CID_LENGTH))
                .set(DtlsConfig.DTLS_CONNECTION_ID_LENGTH, CID_LENGTH);

        final DTLSConnector clientConnector = new DTLSConnector(clientDtlsBuilder.build());
        final CoapEndpoint clientEndpoint = CoapEndpoint.builder()
                .setConfiguration(clientConfig)
                .setConnector(clientConnector)
                .build();
        clientEndpoint.start();
        endpointsToCleanUp.add(clientEndpoint);

        final CoapClient client = new CoapClient("coaps://127.0.0.1:" + node1PublicAddr.getPort() + "/telemetry");
        client.setEndpoint(clientEndpoint);
        clientsToCleanUp.add(client);

        final CoapResponse response = client.post("health-check-payload", MediaTypeRegistry.TEXT_PLAIN);
        assertNotNull(response, "CoAP response should not be null after dropped foreign packet");
        assertEquals(ResponseCode.CHANGED, response.getCode());
    }

    /**
     * In-memory cache implementation with TTL support for testing.
     */
    private static class InMemoryTestCache implements Cache<String, String> {

        private static class CacheEntry {
            final String value;
            final long expiresAt;

            CacheEntry(final String value, final long expiresAt) {
                this.value = value;
                this.expiresAt = expiresAt;
            }

            boolean isExpired(final long now) {
                return expiresAt > 0 && now >= expiresAt;
            }
        }

        private final ConcurrentMap<String, CacheEntry> store = new ConcurrentHashMap<>();
        private final TestClock clock;

        InMemoryTestCache(final TestClock clock) {
            this.clock = clock;
        }

        @Override
        public Future<JsonObject> checkForCacheAvailability() {
            return Future.succeededFuture(new JsonObject().put("status", "UP"));
        }

        @Override
        public Future<Void> put(final String key, final String value) {
            return put(key, value, -1, TimeUnit.MILLISECONDS);
        }

        @Override
        public Future<Void> put(final String key, final String value, final long lifespan, final TimeUnit lifespanUnit) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            final long expiresAt = lifespan > 0 ? clock.millis() + lifespanUnit.toMillis(lifespan) : -1;
            store.put(key, new CacheEntry(value, expiresAt));
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> putAll(final Map<? extends String, ? extends String> data) {
            return putAll(data, -1, TimeUnit.MILLISECONDS);
        }

        @Override
        public Future<Void> putAll(final Map<? extends String, ? extends String> data, final long lifespan,
                final TimeUnit lifespanUnit) {
            Objects.requireNonNull(data);
            data.forEach((k, v) -> put(k, v, lifespan, lifespanUnit));
            return Future.succeededFuture();
        }

        @Override
        public Future<String> get(final String key) {
            Objects.requireNonNull(key);
            final CacheEntry entry = store.get(key);
            if (entry == null) {
                return Future.succeededFuture(null);
            }
            if (entry.isExpired(clock.millis())) {
                store.remove(key, entry);
                return Future.succeededFuture(null);
            }
            return Future.succeededFuture(entry.value);
        }

        @Override
        public Future<Boolean> remove(final String key, final String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            final CacheEntry entry = store.get(key);
            if (entry == null || entry.isExpired(clock.millis())) {
                store.remove(key);
                return Future.succeededFuture(false);
            }
            if (Objects.equals(entry.value, value)) {
                store.remove(key, entry);
                return Future.succeededFuture(true);
            }
            return Future.succeededFuture(false);
        }

        @Override
        public Future<Map<String, String>> getAll(final Set<? extends String> keys) {
            Objects.requireNonNull(keys);
            final Map<String, String> result = new HashMap<>();
            final long now = clock.millis();
            for (final String key : keys) {
                final CacheEntry entry = store.get(key);
                if (entry != null) {
                    if (entry.isExpired(now)) {
                        store.remove(key, entry);
                    } else {
                        result.put(key, entry.value);
                    }
                }
            }
            return Future.succeededFuture(result);
        }
    }

    /**
     * Mutable clock for deterministic time manipulation in tests.
     */
    private static class TestClock extends Clock {
        private final AtomicLong millis;

        TestClock(final Instant initialInstant) {
            this.millis = new AtomicLong(initialInstant.toEpochMilli());
        }

        void advance(final Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }
}

