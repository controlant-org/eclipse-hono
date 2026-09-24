/**
 * Copyright (c) 2021, 2026 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Optional;

import org.eclipse.hono.adapter.ProtocolAdapterOptions;
import org.eclipse.hono.util.Constants;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring the CoAP protocol adapter.
 *
 */
@ConfigMapping(prefix = "hono.coap", namingStrategy = NamingStrategy.VERBATIM)
public interface CoapAdapterOptions {

    /**
     * Gets the adapter options.
     *
     * @return The options.
     */
    @WithParentName
    ProtocolAdapterOptions adapterOptions();

    /**
     * Gets the regular expression used for splitting up
     * a username into the auth-id and tenant.
     *
     * @return The regex.
     */
    @WithDefault("@")
    String idSplitRegex();

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * all CoAP endpoints.
     *
     * @return The path.
     */
    Optional<String> networkConfig();

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>secure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #networkConfig()}.
     *
     * @return The path.
     */
    Optional<String> secureNetworkConfig();

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>insecure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #networkConfig()}.
     *
     * @return The path.
     */
    Optional<String> insecureNetworkConfig();

    /**
     * Gets the number of threads used for receiving/sending UDP packets.
     * <p>
     * The connector will start the given number of threads for each direction, outbound (sending)
     * as well as inbound (receiving).
     *
     * @return The number of threads.
     */
    @WithDefault("2")
    int connectorThreads();

    /**
     * Gets the number of threads used for processing CoAP message exchanges at the
     * protocol layer.
     *
     * @return The number of threads.
     */
    @WithDefault("2")
    int coapThreads();

    /**
     * Gets the number of threads used for processing DTLS message exchanges at the
     * connection layer.
     *
     * @return The number of threads.
     */
    @WithDefault("32")
    int dtlsThreads();

    /**
     * Gets the DTLS retransmission timeout.
     * <p>
     * Timeout to wait before retransmit a flight, if no response is received.
     *
     * @return The timeout in milliseconds.
     */
    @WithDefault("2000")
    int dtlsRetransmissionTimeout();

    /**
     * Gets the exchange lifetime.
     * <p>
     * Time to keep coap request for deduplication.
     *
     * @return The exchange lifetime in milliseconds.
     */
    @WithDefault("247000")
    int exchangeLifetime();

    /**
     * Checks, if message offloading is enabled.
     * <p>
     * When messages are kept for deduplication, parts of the message could be offloaded to reduce the heap consumption.
     *
     * @return {@code true} enable message offloading, {@code false} disable message offloading.
     */
    @WithDefault("true")
    boolean messageOffloadingEnabled();

    /**
     * Gets the timeout to ACK a CoAP CON request.
     * <p>
     * If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout
     * is reached without response, a separate ACK is sent to prevent the client from retransmitting the CON request.
     *
     * @return The timeout in milliseconds. A value of {@code -1} means to always piggyback the response in an ACK and
     *         never send a separate CON; a value of {@code 0} means to always send an ACK immediately and include the
     *         response in a separate CON.
     */
    @WithDefault("500")
    int timeoutToAck();

    /**
     * Checks if DTLS Connection ID (CID) support is enabled.
     *
     * @return {@code true} if CID is enabled.
     */
    @WithName("dtls.cid.enabled")
    @WithDefault("false")
    boolean cidEnabled();

    /**
     * Gets the length of the Connection ID in bytes.
     *
     * @return The CID length in bytes.
     */
    @WithName("dtls.cid.length")
    @WithDefault("6")
    int cidLength();

    /**
     * Gets the cluster node ID embedded into generated Connection IDs.
     *
     * @return The node ID, or {@link Constants#PORT_UNCONFIGURED} if unset / auto-assigned.
     */
    @WithName("dtls.cid.node-id")
    @WithDefault(Constants.PORT_UNCONFIGURED_STRING)
    int cidNodeId();

    /**
     * Checks if DTLS cluster forwarding mesh is enabled.
     *
     * @return {@code true} if clustering is enabled.
     */
    @WithName("dtls.cluster.enabled")
    @WithDefault("false")
    boolean clusterEnabled();

    /**
     * Gets the port used for cluster-internal UDP mesh forwarding.
     *
     * @return The cluster port.
     */
    @WithName("dtls.cluster.port")
    @WithDefault("5685")
    int clusterPort();

    /**
     * Gets the local network interface IP address to bind the cluster connector to.
     *
     * @return The bind address.
     */
    @WithName("dtls.cluster.bind-address")
    @WithDefault("0.0.0.0")
    String clusterBindAddress();

    /**
     * Gets the shared secret used for MAC authentication of cluster forward messages.
     *
     * @return The MAC secret, or empty if none configured.
     */
    @WithName("dtls.cluster.mac-secret")
    Optional<String> clusterMacSecret();

    /**
     * Gets the interval between cluster node heartbeat registrations.
     *
     * @return The heartbeat interval.
     */
    @WithName("dtls.cluster.heartbeat")
    @WithDefault("PT10S")
    Duration clusterHeartbeat();

    /**
     * Gets the time-to-live for node registrations in the cluster registry.
     *
     * @return The node TTL.
     */
    @WithName("dtls.cluster.node-ttl")
    @WithDefault("PT30S")
    Duration clusterNodeTtl();

    /**
     * Checks if DTLS session resumption is enabled.
     *
     * @return {@code true} if session resumption is enabled.
     */
    @WithName("dtls.session-resumption.enabled")
    @WithDefault("true")
    boolean sessionResumptionEnabled();
}

