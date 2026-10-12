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

import java.util.Objects;

import org.eclipse.californium.scandium.DtlsClusterHealthLogger;
import org.eclipse.hono.adapter.coap.CoapAdapterMetrics;

/**
 * A health handler for a DTLS cluster connector that reports the records exchanged with other
 * cluster nodes to the CoAP adapter's metrics.
 * <p>
 * The handler extends Californium's default health handler for cluster connectors, so that the
 * connector's periodic health log remains available.
 */
public class MetricsReportingDtlsClusterHealth extends DtlsClusterHealthLogger {

    private final CoapAdapterMetrics metrics;

    /**
     * Creates a new health handler.
     *
     * @param tag The logging tag to use.
     * @param metrics The metrics to report to.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MetricsReportingDtlsClusterHealth(final String tag, final CoapAdapterMetrics metrics) {
        super(Objects.requireNonNull(tag));
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public void forwardMessage() {
        super.forwardMessage();
        metrics.reportClusterRecordForwarded(CoapAdapterMetrics.TAG_VALUE_OUTBOUND);
    }

    @Override
    public void processForwardedMessage() {
        super.processForwardedMessage();
        metrics.reportClusterRecordForwarded(CoapAdapterMetrics.TAG_VALUE_INBOUND);
    }

    @Override
    public void backwardMessage() {
        super.backwardMessage();
        metrics.reportClusterRecordBackwarded(CoapAdapterMetrics.TAG_VALUE_OUTBOUND);
    }

    @Override
    public void sendBackwardedMessage() {
        super.sendBackwardedMessage();
        metrics.reportClusterRecordBackwarded(CoapAdapterMetrics.TAG_VALUE_INBOUND);
    }

    @Override
    public void dropForwardMessage() {
        super.dropForwardMessage();
        metrics.reportClusterRecordDropped(
                CoapAdapterMetrics.TAG_VALUE_FORWARD, CoapAdapterMetrics.DROP_REASON_UNDELIVERABLE);
    }

    @Override
    public void dropBackwardMessage() {
        super.dropBackwardMessage();
        metrics.reportClusterRecordDropped(
                CoapAdapterMetrics.TAG_VALUE_BACKWARD, CoapAdapterMetrics.DROP_REASON_UNDELIVERABLE);
    }

    @Override
    public void badForwardMessage() {
        super.badForwardMessage();
        metrics.reportClusterRecordDropped(
                CoapAdapterMetrics.TAG_VALUE_FORWARD, CoapAdapterMetrics.DROP_REASON_MAC_INVALID);
    }

    @Override
    public void badBackwardMessage() {
        super.badBackwardMessage();
        metrics.reportClusterRecordDropped(
                CoapAdapterMetrics.TAG_VALUE_BACKWARD, CoapAdapterMetrics.DROP_REASON_MAC_INVALID);
    }
}
