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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.DtlsClusterConnector;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.ConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.MultiNodeConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.SingleNodeConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link ConfigBasedCoapEndpointFactory}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class ConfigBasedCoapEndpointFactoryTest {

    private static Vertx vertx;
    private CoapAdapterProperties properties;

    /**
     * Initializes Vert.x instance.
     */
    @BeforeAll
    public static void beforeAll() {
        vertx = Vertx.vertx();
    }

    /**
     * Closes Vert.x instance.
     */
    @AfterAll
    public static void afterAll() {
        if (vertx != null) {
            vertx.close();
        }
    }

    /**
     * Sets up test properties before each test.
     */
    @BeforeEach
    public void setUp() {
        properties = new CoapAdapterProperties();
        properties.setPort(5684);
    }

    private static DtlsConnectorConfig getDtlsConnectorConfig(final DTLSConnector connector) throws Exception {
        final Field configField = DTLSConnector.class.getDeclaredField("config");
        configField.setAccessible(true);
        return (DtlsConnectorConfig) configField.get(connector);
    }

    private static ConnectionIdGenerator getConnectionIdGenerator(final DTLSConnector connector) throws Exception {
        final Field cidField = DTLSConnector.class.getDeclaredField("connectionIdGenerator");
        cidField.setAccessible(true);
        return (ConnectionIdGenerator) cidField.get(connector);
    }

    /**
     * Verifies that the default configuration produces a standard DTLSConnector with session ID disabled.
     *
     * @param ctx The test context.
     */
    @Test
    void testDefaultConfigurationProducesStandardDtlsConnector(final VertxTestContext ctx) {
        final ConfigBasedCoapEndpointFactory factory = new ConfigBasedCoapEndpointFactory(vertx, properties);
        factory.setPskStore(mock(AdvancedPskStore.class));

        factory.getSecureEndpoint().onComplete(ctx.succeeding(endpoint -> {
            ctx.verify(() -> {
                assertNotNull(endpoint);
                final Object connector = ((CoapEndpoint) endpoint).getConnector();
                assertInstanceOf(DTLSConnector.class, connector);
                assertFalse(connector instanceof DtlsClusterConnector, "connector should not be DtlsClusterConnector");

                final DTLSConnector dtlsConnector = (DTLSConnector) connector;
                final DtlsConnectorConfig dtlsConfig = getDtlsConnectorConfig(dtlsConnector);
                assertFalse(dtlsConfig.useServerSessionId());
                assertNull(dtlsConfig.getSessionStore());
                assertNull(getConnectionIdGenerator(dtlsConnector));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that enabling CID in single-node mode configures a SingleNodeConnectionIdGenerator.
     *
     * @param ctx The test context.
     */
    @Test
    void testCidEnabledProducesSingleNodeConnectionIdGenerator(final VertxTestContext ctx) {
        properties.setCidEnabled(true);
        properties.setCidLength(6);

        final ConfigBasedCoapEndpointFactory factory = new ConfigBasedCoapEndpointFactory(vertx, properties);
        factory.setPskStore(mock(AdvancedPskStore.class));

        factory.getSecureEndpoint().onComplete(ctx.succeeding(endpoint -> {
            ctx.verify(() -> {
                assertNotNull(endpoint);
                final Object connector = ((CoapEndpoint) endpoint).getConnector();
                assertInstanceOf(DTLSConnector.class, connector);
                assertFalse(connector instanceof DtlsClusterConnector, "connector should not be DtlsClusterConnector");

                final DTLSConnector dtlsConnector = (DTLSConnector) connector;
                final ConnectionIdGenerator cidGen = getConnectionIdGenerator(dtlsConnector);
                assertInstanceOf(SingleNodeConnectionIdGenerator.class, cidGen);

                final DtlsConnectorConfig dtlsConfig = getDtlsConnectorConfig(dtlsConnector);
                assertEquals(6, dtlsConfig.getConfiguration().get(DtlsConfig.DTLS_CONNECTION_ID_LENGTH));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that enabling cluster mode produces a DtlsClusterConnector with MultiNodeConnectionIdGenerator.
     *
     * @param ctx The test context.
     */
    @Test
    void testClusterEnabledProducesDtlsClusterConnector(final VertxTestContext ctx) {
        properties.setClusterEnabled(true);
        properties.setCidNodeId(2);
        properties.setCidLength(6);
        properties.setClusterPort(5685);
        properties.setClusterBindAddress("127.0.0.1");
        properties.setClusterMacSecret("test-secret");

        final ConfigBasedCoapEndpointFactory factory = new ConfigBasedCoapEndpointFactory(vertx, properties);
        factory.setPskStore(mock(AdvancedPskStore.class));
        final DtlsClusterConnector.ClusterNodesProvider nodesProvider = mock(DtlsClusterConnector.ClusterNodesProvider.class);
        factory.setClusterNodesProvider(nodesProvider);

        factory.getSecureEndpoint().onComplete(ctx.succeeding(endpoint -> {
            ctx.verify(() -> {
                assertNotNull(endpoint);
                final Object connector = ((CoapEndpoint) endpoint).getConnector();
                assertInstanceOf(DtlsClusterConnector.class, connector);

                final DtlsClusterConnector clusterConnector = (DtlsClusterConnector) connector;
                assertEquals(2, clusterConnector.getNodeID());
                assertEquals("127.0.0.1", clusterConnector.getClusterInternalAddress().getHostString());
                assertEquals(5685, clusterConnector.getClusterInternalAddress().getPort());

                final ConnectionIdGenerator cidGen = getConnectionIdGenerator(clusterConnector);
                assertInstanceOf(MultiNodeConnectionIdGenerator.class, cidGen);
                final MultiNodeConnectionIdGenerator multiNodeCidGen = (MultiNodeConnectionIdGenerator) cidGen;
                assertEquals(2, multiNodeCidGen.getNodeId());

                final DtlsConnectorConfig dtlsConfig = getDtlsConnectorConfig(clusterConnector);
                assertEquals(6, dtlsConfig.getConfiguration().get(DtlsConfig.DTLS_CONNECTION_ID_LENGTH));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that cluster mode fails when ClusterNodesProvider is missing.
     *
     * @param ctx The test context.
     */
    @Test
    void testClusterEnabledFailsIfClusterNodesProviderMissing(final VertxTestContext ctx) {
        properties.setClusterEnabled(true);
        properties.setCidNodeId(2);

        final ConfigBasedCoapEndpointFactory factory = new ConfigBasedCoapEndpointFactory(vertx, properties);
        factory.setPskStore(mock(AdvancedPskStore.class));
        // clusterNodesProvider is NOT set

        factory.getSecureEndpoint().onComplete(ctx.failing(cause -> {
            ctx.verify(() -> {
                assertInstanceOf(IllegalStateException.class, cause);
                assertEquals("clusterNodesProvider must be set when cluster mode is enabled", cause.getMessage());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that cluster mode fails when cidNodeId is negative.
     *
     * @param ctx The test context.
     */
    @Test
    void testClusterEnabledFailsIfCidNodeIdNegative(final VertxTestContext ctx) {
        properties.setClusterEnabled(true);
        properties.setCidNodeId(-1);

        final ConfigBasedCoapEndpointFactory factory = new ConfigBasedCoapEndpointFactory(vertx, properties);
        factory.setPskStore(mock(AdvancedPskStore.class));
        factory.setClusterNodesProvider(mock(DtlsClusterConnector.ClusterNodesProvider.class));

        factory.getSecureEndpoint().onComplete(ctx.failing(cause -> {
            ctx.verify(() -> {
                assertInstanceOf(IllegalStateException.class, cause);
                assertEquals("cidNodeId must be configured when cluster mode is enabled", cause.getMessage());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that cluster connector is successfully created even without MAC secret.
     *
     * @param ctx The test context.
     */
    @Test
    void testClusterEnabledWithoutMacSecret(final VertxTestContext ctx) {
        properties.setClusterEnabled(true);
        properties.setCidNodeId(0);
        properties.setClusterMacSecret(null);

        final ConfigBasedCoapEndpointFactory factory = new ConfigBasedCoapEndpointFactory(vertx, properties);
        factory.setPskStore(mock(AdvancedPskStore.class));
        factory.setClusterNodesProvider(mock(DtlsClusterConnector.ClusterNodesProvider.class));

        factory.getSecureEndpoint().onComplete(ctx.succeeding(endpoint -> {
            ctx.verify(() -> {
                assertNotNull(endpoint);
                assertInstanceOf(DtlsClusterConnector.class, ((CoapEndpoint) endpoint).getConnector());
            });
            ctx.completeNow();
        }));
    }
}

