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
    }

    @Override
    public void reportClusterRecordForwarded(final String direction) {
        Counter.builder(METER_COAP_DTLS_CLUSTER_FORWARDED)
                .description("DTLS records forwarded to or from the cluster node owning the connection ID")
                .tag(TAG_DIRECTION, direction)
                .register(registry)
                .increment();
    }

    @Override
    public void reportClusterRecordBackwarded(final String direction) {
        Counter.builder(METER_COAP_DTLS_CLUSTER_BACKWARDED)
                .description("DTLS records sent back via the cluster node that has received the device's records")
                .tag(TAG_DIRECTION, direction)
                .register(registry)
                .increment();
    }

    @Override
    public void reportClusterRecordDropped(final String path, final String reason) {
        Counter.builder(METER_COAP_DTLS_CLUSTER_DROPPED)
                .description("DTLS records that could not be exchanged with another cluster node")
                .tag(TAG_PATH, path)
                .tag(TAG_REASON, reason)
                .register(registry)
                .increment();
    }
}
