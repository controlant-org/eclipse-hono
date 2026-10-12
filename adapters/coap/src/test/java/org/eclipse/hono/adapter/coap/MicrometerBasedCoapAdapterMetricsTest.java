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

    /**
     * Verifies that forwarded records are counted per direction.
     */
    @Test
    void testClusterRecordForwardedMetric() {
        metrics.reportClusterRecordForwarded(CoapAdapterMetrics.TAG_VALUE_OUTBOUND);
        metrics.reportClusterRecordForwarded(CoapAdapterMetrics.TAG_VALUE_OUTBOUND);
        metrics.reportClusterRecordForwarded(CoapAdapterMetrics.TAG_VALUE_INBOUND);

        assertEquals(2.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CLUSTER_FORWARDED)
                .tag(CoapAdapterMetrics.TAG_DIRECTION, CoapAdapterMetrics.TAG_VALUE_OUTBOUND).counter().count());
        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CLUSTER_FORWARDED)
                .tag(CoapAdapterMetrics.TAG_DIRECTION, CoapAdapterMetrics.TAG_VALUE_INBOUND).counter().count());
    }

    /**
     * Verifies that backwarded records are counted per direction.
     */
    @Test
    void testClusterRecordBackwardedMetric() {
        metrics.reportClusterRecordBackwarded(CoapAdapterMetrics.TAG_VALUE_INBOUND);

        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CLUSTER_BACKWARDED)
                .tag(CoapAdapterMetrics.TAG_DIRECTION, CoapAdapterMetrics.TAG_VALUE_INBOUND).counter().count());
    }

    /**
     * Verifies that dropped records are counted per path and reason.
     */
    @Test
    void testClusterRecordDroppedMetric() {
        metrics.reportClusterRecordDropped(CoapAdapterMetrics.TAG_VALUE_FORWARD, CoapAdapterMetrics.DROP_REASON_MAC_INVALID);

        assertEquals(1.0, registry.get(CoapAdapterMetrics.METER_COAP_DTLS_CLUSTER_DROPPED)
                .tag(CoapAdapterMetrics.TAG_PATH, CoapAdapterMetrics.TAG_VALUE_FORWARD)
                .tag(CoapAdapterMetrics.TAG_REASON, CoapAdapterMetrics.DROP_REASON_MAC_INVALID)
                .counter().count());
    }
}
