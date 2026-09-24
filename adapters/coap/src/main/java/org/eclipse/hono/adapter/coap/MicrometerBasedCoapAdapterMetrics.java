/*******************************************************************************
 * Copyright (c) 2018, 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.adapter.coap;

import org.eclipse.hono.adapter.MicrometerBasedProtocolAdapterMetrics;
import org.eclipse.hono.adapter.ProtocolAdapterProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * Metrics for the COAP based adapters.
 */
public class MicrometerBasedCoapAdapterMetrics extends MicrometerBasedProtocolAdapterMetrics implements CoapAdapterMetrics {

    private final Counter cidReceivedCounter;
    private final Counter clusterForwardedOutboundCounter;
    private final Counter clusterForwardedInboundCounter;
    private final Counter resumptionSuccessCounter;
    private final Counter resumptionFailureCounter;

    /**
     * Create a new metrics instance for COAP adapters.
     *
     * @param registry The meter registry to use.
     * @param vertx The Vert.x instance to use.
     * @param config The adapter properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MicrometerBasedCoapAdapterMetrics(
            final MeterRegistry registry,
            final Vertx vertx,
            final ProtocolAdapterProperties config) {
        super(registry, vertx, config);

        this.cidReceivedCounter = Counter.builder(METER_COAP_DTLS_CID_RECEIVED)
                .description("Number of received DTLS packets containing a Connection ID")
                .register(registry);
        this.clusterForwardedOutboundCounter = Counter.builder(METER_COAP_DTLS_CLUSTER_FORWARDED)
                .description("Number of datagrams forwarded to another cluster node")
                .tag("direction", "outbound")
                .register(registry);
        this.clusterForwardedInboundCounter = Counter.builder(METER_COAP_DTLS_CLUSTER_FORWARDED)
                .description("Number of datagrams received from another cluster node")
                .tag("direction", "inbound")
                .register(registry);
        this.resumptionSuccessCounter = Counter.builder(METER_COAP_DTLS_RESUMPTION)
                .description("Number of successful DTLS session resumptions")
                .tag("outcome", "succeeded")
                .register(registry);
        this.resumptionFailureCounter = Counter.builder(METER_COAP_DTLS_RESUMPTION)
                .description("Number of failed DTLS session resumptions")
                .tag("outcome", "failed")
                .register(registry);
    }

    @Override
    public void incrementCidReceived() {
        this.cidReceivedCounter.increment();
    }

    @Override
    public void incrementClusterForwardedOutbound() {
        this.clusterForwardedOutboundCounter.increment();
    }

    @Override
    public void incrementClusterForwardedInbound() {
        this.clusterForwardedInboundCounter.increment();
    }

    @Override
    public void incrementClusterForwardDropped(final String reason) {
        Counter.builder(METER_COAP_DTLS_CLUSTER_FORWARD_DROPPED)
                .description("Number of cluster forward datagrams dropped")
                .tag("reason", reason != null ? reason : "unknown")
                .register(this.registry)
                .increment();
    }

    @Override
    public void incrementResumption(final boolean success) {
        if (success) {
            this.resumptionSuccessCounter.increment();
        } else {
            this.resumptionFailureCounter.increment();
        }
    }
}
