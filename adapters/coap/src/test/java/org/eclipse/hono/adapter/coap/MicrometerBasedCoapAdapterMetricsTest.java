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
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;

/**
 * Tests verifying behavior of {@link MicrometerBasedCoapAdapterMetrics}.
 */
public class MicrometerBasedCoapAdapterMetricsTest {

    private MeterRegistry registry;
    private Vertx vertx;
    private CoapAdapterProperties config;
    private MicrometerBasedCoapAdapterMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        vertx = mock(Vertx.class);
        config = new CoapAdapterProperties();
        metrics = new MicrometerBasedCoapAdapterMetrics(registry, vertx, config);
    }

    @Test
    void testCidReceivedMetric() {
        metrics.incrementCidReceived();
        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CID_RECEIVED).counter().count());
    }

    @Test
    void testClusterForwardedMetrics() {
        metrics.incrementClusterForwardedOutbound();
        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CLUSTER_FORWARDED)
                .tag("direction", "outbound").counter().count());

        metrics.incrementClusterForwardedInbound();
        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CLUSTER_FORWARDED)
                .tag("direction", "inbound").counter().count());
    }

    @Test
    void testClusterForwardDroppedMetric() {
        metrics.incrementClusterForwardDropped("node_unknown");
        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CLUSTER_FORWARD_DROPPED)
                .tag("reason", "node_unknown").counter().count());
    }

    @Test
    void testResumptionMetrics() {
        metrics.incrementResumption(true);
        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_RESUMPTION)
                .tag("outcome", "succeeded").counter().count());

        metrics.incrementResumption(false);
        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_RESUMPTION)
                .tag("outcome", "failed").counter().count());
    }

    @Test
    void testNoopMetrics() {
        // Ensure NOOP instance methods execute without errors
        CoapAdapterMetrics.NOOP.incrementCidReceived();
        CoapAdapterMetrics.NOOP.incrementClusterForwardedOutbound();
        CoapAdapterMetrics.NOOP.incrementClusterForwardedInbound();
        CoapAdapterMetrics.NOOP.incrementClusterForwardDropped("reason");
        CoapAdapterMetrics.NOOP.incrementResumption(true);
    }
}
