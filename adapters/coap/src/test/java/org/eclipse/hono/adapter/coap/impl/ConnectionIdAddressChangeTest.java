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

package org.eclipse.hono.adapter.coap.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.SingleNodeConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedSinglePskStore;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.vertx.core.Vertx;

/**
 * Tests verifying that a device keeps its DTLS connection with a single adapter instance when its address
 * changes, if the secure endpoint has been created with connection IDs enabled.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
public class ConnectionIdAddressChangeTest {

    private static final String PSK_IDENTITY = "device-1@DEFAULT_TENANT";
    private static final byte[] PSK_KEY = "secret-key".getBytes(StandardCharsets.UTF_8);
    private static final int CID_LENGTH = 6;

    private static Vertx vertx;

    private CoapServer server;
    private CoapEndpoint clientEndpoint;
    private CoapClient client;
    private NatRelay relay;

    /**
     * Creates the Vert.x instance.
     */
    @BeforeAll
    public static void createVertx() {
        vertx = Vertx.vertx();
    }

    /**
     * Closes the Vert.x instance.
     */
    @AfterAll
    public static void closeVertx() {
        vertx.close();
    }

    /**
     * Stops the client, the relay and the server.
     */
    @AfterEach
    public void stopAll() {
        if (client != null) {
            client.shutdown();
        }
        if (clientEndpoint != null) {
            clientEndpoint.destroy();
        }
        if (relay != null) {
            relay.close();
        }
        if (server != null) {
            server.destroy();
        }
    }

    /**
     * Verifies that a device with a connection ID can keep using its DTLS connection after
     * its address has changed.
     *
     * @throws Exception if the test fails.
     */
    @Test
    public void testConnectionSurvivesAddressChangeWithConnectionId() throws Exception {
        startServerAndClient(true);

        assertRequestSucceeds("before address change");
        relay.changeAddress();

        assertRequestSucceeds("after address change");
    }

    /**
     * Verifies that a device without a connection ID cannot use its DTLS connection after its address
     * has changed, which shows that the relay changes the device's address as seen by the adapter.
     *
     * @throws Exception if the test fails.
     */
    @Test
    public void testConnectionDoesNotSurviveAddressChangeWithoutConnectionId() throws Exception {
        startServerAndClient(false);

        assertRequestSucceeds("before address change");
        relay.changeAddress();

        assertNull(client.post("after address change", MediaTypeRegistry.TEXT_PLAIN));
    }

    private void startServerAndClient(final boolean cidEnabled) throws IOException {
        final CoapAdapterProperties properties = new CoapAdapterProperties();
        properties.setBindAddress(Constants.LOOPBACK_DEVICE_ADDRESS);
        properties.setPort(0);
        properties.setCidEnabled(cidEnabled);
        properties.setCidLength(CID_LENGTH);
        final ConfigBasedCoapEndpointFactory factory = new ConfigBasedCoapEndpointFactory(vertx, properties);
        factory.setPskStore(new AdvancedSinglePskStore(PSK_IDENTITY, PSK_KEY));
        final Endpoint secureEndpoint = factory.getSecureEndpoint()
                .toCompletionStage().toCompletableFuture().join();

        server = new CoapServer(factory.getCoapServerConfiguration()
                .toCompletionStage().toCompletableFuture().join());
        server.addEndpoint(secureEndpoint);
        server.add(new CoapResource("telemetry") {
            @Override
            public void handlePOST(final CoapExchange exchange) {
                exchange.respond(ResponseCode.CHANGED);
            }
        });
        server.start();

        relay = new NatRelay(secureEndpoint.getAddress());

        final Configuration clientConfig = Configuration.createStandardWithoutFile();
        final DTLSConnector clientConnector = new DTLSConnector(DtlsConnectorConfig.builder(clientConfig)
                .setAddress(new InetSocketAddress(Constants.LOOPBACK_DEVICE_ADDRESS, 0))
                .setAdvancedPskStore(new AdvancedSinglePskStore(PSK_IDENTITY, PSK_KEY))
                .setConnectionIdGenerator(new SingleNodeConnectionIdGenerator(CID_LENGTH))
                .set(DtlsConfig.DTLS_CONNECTION_ID_LENGTH, CID_LENGTH)
                .build());
        clientEndpoint = CoapEndpoint.builder()
                .setConfiguration(clientConfig)
                .setConnector(clientConnector)
                .build();
        clientEndpoint.start();
        client = new CoapClient("coaps://%s:%d/telemetry".formatted(
                Constants.LOOPBACK_DEVICE_ADDRESS, relay.getAddress().getPort()));
        client.setEndpoint(clientEndpoint);
        client.setTimeout(2000L);
    }

    private void assertRequestSucceeds(final String payload) throws Exception {
        final CoapResponse response = client.post(payload, MediaTypeRegistry.TEXT_PLAIN);
        assertNotNull(response, "no response for request " + payload);
        assertEquals(ResponseCode.CHANGED, response.getCode());
    }

    /**
     * A UDP relay between a device and the adapter that can change the address it uses for sending the
     * device's datagrams to the adapter, like a NAT gateway that assigns a new port to an existing flow.
     */
    private static final class NatRelay implements AutoCloseable {

        private final DatagramSocket deviceSide;
        private final InetSocketAddress adapterAddress;
        private volatile DatagramSocket adapterSide;
        private volatile SocketAddress deviceAddress;

        NatRelay(final InetSocketAddress adapterAddress) throws IOException {
            this.adapterAddress = adapterAddress;
            this.deviceSide = new DatagramSocket(new InetSocketAddress(Constants.LOOPBACK_DEVICE_ADDRESS, 0));
            this.adapterSide = openAdapterSide();
            relay(deviceSide, packet -> {
                deviceAddress = packet.getSocketAddress();
                adapterSide.send(new DatagramPacket(
                        packet.getData(), packet.getOffset(), packet.getLength(), this.adapterAddress));
            });
        }

        InetSocketAddress getAddress() {
            return (InetSocketAddress) deviceSide.getLocalSocketAddress();
        }

        /**
         * Sends the device's subsequent datagrams to the adapter from a new port.
         *
         * @throws IOException if the new socket cannot be opened.
         */
        void changeAddress() throws IOException {
            final DatagramSocket previous = adapterSide;
            adapterSide = openAdapterSide();
            previous.close();
        }

        private DatagramSocket openAdapterSide() throws IOException {
            final DatagramSocket socket = new DatagramSocket(new InetSocketAddress(Constants.LOOPBACK_DEVICE_ADDRESS, 0));
            relay(socket, packet -> deviceSide.send(new DatagramPacket(
                    packet.getData(), packet.getOffset(), packet.getLength(), deviceAddress)));
            return socket;
        }

        @Override
        public void close() {
            deviceSide.close();
            adapterSide.close();
        }

        private interface PacketHandler {
            void handle(DatagramPacket packet) throws IOException;
        }

        private static void relay(final DatagramSocket socket, final PacketHandler handler) {
            final Thread thread = new Thread(() -> {
                final byte[] buffer = new byte[2048];
                try {
                    while (!socket.isClosed()) {
                        final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        handler.handle(packet);
                    }
                } catch (final IOException e) {
                    // the socket has been closed
                }
            }, "nat-relay-" + socket.getLocalPort());
            thread.setDaemon(true);
            thread.start();
        }
    }
}
