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
     * The name of the meter for received DTLS packets containing a Connection ID (CID).
     */
    String METER_COAP_DTLS_CID_RECEIVED = "hono.coap.dtls.cid.received";

    /**
     * The name of the meter for datagrams forwarded between cluster nodes.
     */
    String METER_COAP_DTLS_CLUSTER_FORWARDED = "hono.coap.dtls.cluster.forwarded";

    /**
     * The name of the meter for cluster forward datagrams dropped.
     */
    String METER_COAP_DTLS_CLUSTER_FORWARD_DROPPED = "hono.coap.dtls.cluster.forward.dropped";

    /**
     * The tag name for cluster forwarding direction.
     */
    String TAG_DIRECTION = "direction";

    /**
     * The tag value for outbound cluster forwarding.
     */
    String TAG_VALUE_OUTBOUND = "outbound";

    /**
     * The tag value for inbound cluster forwarding.
     */
    String TAG_VALUE_INBOUND = "inbound";

    /**
     * The tag name for cluster forward drop reason.
     */
    String TAG_REASON = "reason";

    /**
     * Drop reason when the target node is offline or unreachable.
     */
    String DROP_REASON_NODE_OFFLINE = "node_offline";

    /**
     * Drop reason when the message MAC is invalid.
     */
    String DROP_REASON_MAC_INVALID = "mac_invalid";

    /**
     * Drop reason when the cluster forward message is malformed.
     */
    String DROP_REASON_MALFORMED = "malformed";

    /**
     * Drop reason when the drop reason is unknown.
     */
    String DROP_REASON_UNKNOWN = "unknown";

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
     * Increments the counter for received datagrams with DTLS Connection ID (CID).
     */
    default void incrementCidReceived() {
    }

    /**
     * Increments the counter for outbound datagrams forwarded to another cluster node.
     */
    default void incrementClusterForwardedOutbound() {
    }

    /**
     * Increments the counter for inbound datagrams forwarded from another cluster node.
     */
    default void incrementClusterForwardedInbound() {
    }

    /**
     * Increments the counter for datagrams dropped during cluster forwarding.
     *
     * @param reason The reason why the datagram was dropped.
     */
    default void incrementClusterForwardDropped(final String reason) {
    }
}

