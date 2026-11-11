/**
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
 */

package org.eclipse.hono.adapter.coap;

import org.eclipse.hono.service.metric.Metrics;
import org.eclipse.hono.service.metric.NoopBasedMetrics;

/**
 * Metrics for the COAP based adapters.
 */
public interface CoapAdapterMetrics extends Metrics {

    /**
     * The name of the meter for DTLS records that one cluster node forwards to the cluster node that
     * owns the connection ID contained in the record.
     */
    String METER_COAP_DTLS_CLUSTER_FORWARDED = "hono.coap.dtls.cluster.forwarded";

    /**
     * The name of the meter for DTLS records that the cluster node owning a connection ID sends back
     * via the cluster node that has received the device's records.
     */
    String METER_COAP_DTLS_CLUSTER_BACKWARDED = "hono.coap.dtls.cluster.backwarded";

    /**
     * The name of the meter for DTLS records that could not be exchanged between cluster nodes.
     */
    String METER_COAP_DTLS_CLUSTER_DROPPED = "hono.coap.dtls.cluster.dropped";

    /**
     * The tag name for the direction in which a record is exchanged with another cluster node.
     */
    String TAG_DIRECTION = "direction";

    /**
     * The tag value for a record that is sent to another cluster node.
     */
    String TAG_VALUE_OUTBOUND = "outbound";

    /**
     * The tag value for a record that is received from another cluster node.
     */
    String TAG_VALUE_INBOUND = "inbound";

    /**
     * The tag name for the path on which a record has been dropped.
     */
    String TAG_PATH = "path";

    /**
     * The tag value for records that are forwarded to the cluster node owning a connection ID.
     */
    String TAG_VALUE_FORWARD = "forward";

    /**
     * The tag value for records that are sent back via the cluster node that has received the device's records.
     */
    String TAG_VALUE_BACKWARD = "backward";

    /**
     * The tag name for the reason why a record has been dropped.
     */
    String TAG_REASON = "reason";

    /**
     * Drop reason for records that could not be delivered, e.g. because the other cluster node is unknown
     * or not available, or because the record is malformed.
     */
    String DROP_REASON_UNDELIVERABLE = "undeliverable";

    /**
     * Drop reason for records received from another cluster node with an invalid MAC.
     */
    String DROP_REASON_MAC_INVALID = "mac_invalid";

    /**
     * A no-op implementation for this specific metrics type.
     */
    final class Noop extends NoopBasedMetrics implements CoapAdapterMetrics {

        private Noop() {
        }
    }

    /**
     * The no-op implementation.
     */
    CoapAdapterMetrics NOOP = new Noop();

    /**
     * Reports a DTLS record that has been forwarded to or from the cluster node owning the record's connection ID.
     *
     * @param direction {@value #TAG_VALUE_OUTBOUND} if this node has forwarded the record to the owning node,
     *                  or {@value #TAG_VALUE_INBOUND} if this node owns the connection ID and has received the
     *                  record from the node that it has been sent to.
     */
    default void reportClusterRecordForwarded(final String direction) {
    }

    /**
     * Reports a DTLS record that has been sent back via the cluster node that has received the device's records.
     *
     * @param direction {@value #TAG_VALUE_OUTBOUND} if this node owns the connection ID and has sent the record
     *                  to the other node, or {@value #TAG_VALUE_INBOUND} if this node has received the record
     *                  from the owning node and has sent it to the device.
     */
    default void reportClusterRecordBackwarded(final String direction) {
    }

    /**
     * Reports a DTLS record that could not be exchanged with another cluster node.
     *
     * @param path {@value #TAG_VALUE_FORWARD} or {@value #TAG_VALUE_BACKWARD}.
     * @param reason {@value #DROP_REASON_UNDELIVERABLE} or {@value #DROP_REASON_MAC_INVALID}.
     */
    default void reportClusterRecordDropped(final String path, final String reason) {
    }
}
