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

import java.time.Duration;
import java.util.Objects;

import org.eclipse.hono.adapter.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;

/**
 * Properties for configuring the CoAP protocol adapter.
 */
public class CoapAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default regular expression to split the identity into authority and tenant.
     */
    public static final String DEFAULT_ID_SPLIT_REGEX = "@";
    /**
     * The default number of threads for the connector.
     */
    public static final int DEFAULT_CONNECTOR_THREADS;
    /**
     * The default number of threads for the coap protocol stack.
     */
    public static final int DEFAULT_COAP_THREADS;
    /**
     * The default number of threads for the dtls connector.
     */
    public static final int DEFAULT_DTLS_THREADS;
    /**
     * The default timeout in milliseconds for DTLS retransmissions.
     */
    public static final int DEFAULT_DTLS_RETRANSMISSION_TIMEOUT = 2000;
    /**
     * The default exchange lifetime in milliseconds.
     *
     * According <a href= "https://tools.ietf.org/html/rfc7252#page-30"> RFC 7252 - 4.8. Transmission Parameters</a> the
     * default value is 247 seconds. Such a large time requires also a huge amount of heap. That time includes a
     * processing time of 100s and retransmissions of CON messages. Therefore a practical value could be much smaller.
     */
    public static final int DEFAULT_EXCHANGE_LIFETIME = 247000;
    /**
     * The default for message offloading. When messages are kept for deduplication, some parts of the messages are not
     * longer required and could be release earlier. That helps to reduce the heap consumption.
     */
    public static final boolean DEFAULT_MESSAGE_OFFLOADING = true;
    /**
     * The default timeout in milliseconds to send a separate ACK.
     */
    public static final int DEFAULT_TIMEOUT_TO_ACK = 500;
    /**
     * The default blockwise status lifetime in milliseconds.
     *
     * Time the blockwise status is kept with further message exchanges. If a blockwise transfer is not continued for
     * that time, the status gets removed and a new request will fail.
     */
    public static final int DEFAULT_BLOCKWISE_STATUS_LIFETIME = 300000;
    /**
     * The default for enabling DTLS Connection ID (CID) support.
     */
    public static final boolean DEFAULT_CID_ENABLED = false;
    /**
     * The default length in bytes for generated Connection IDs.
     */
    public static final int DEFAULT_CID_LENGTH = 6;
    /**
     * The default cluster node ID embedded in Connection IDs.
     */
    public static final int DEFAULT_CID_NODE_ID = Constants.PORT_UNCONFIGURED;
    /**
     * The default for enabling the DTLS cluster mesh connector.
     */
    public static final boolean DEFAULT_CLUSTER_ENABLED = false;
    /**
     * The default port for the cluster connector.
     */
    public static final int DEFAULT_CLUSTER_PORT = 5685;
    /**
     * The default bind address for the cluster connector.
     */
    public static final String DEFAULT_CLUSTER_BIND_ADDRESS = "0.0.0.0";
    /**
     * The default interval for cluster heartbeat registrations.
     */
    public static final Duration DEFAULT_CLUSTER_HEARTBEAT = Duration.ofSeconds(10);
    /**
     * The default TTL for cluster node entries.
     */
    public static final Duration DEFAULT_CLUSTER_NODE_TTL = Duration.ofSeconds(30);

    static {
        DEFAULT_CONNECTOR_THREADS = 2;
        final int cpu = Runtime.getRuntime().availableProcessors();
        if (cpu < 4) {
            DEFAULT_COAP_THREADS = 4;
            DEFAULT_DTLS_THREADS = 4;
        } else {
            DEFAULT_COAP_THREADS = cpu;
            DEFAULT_DTLS_THREADS = cpu;
        }
    }

    private String idSplitRegex = DEFAULT_ID_SPLIT_REGEX;
    private String networkConfig = null;
    private String secureNetworkConfig = null;
    private String insecureNetworkConfig = null;
    private int connectorThreads = DEFAULT_CONNECTOR_THREADS;
    private int coapThreads = DEFAULT_COAP_THREADS;
    private int dtlsThreads = DEFAULT_DTLS_THREADS;
    private int dtlsRetransmissionTimeout = DEFAULT_DTLS_RETRANSMISSION_TIMEOUT;
    private int exchangeLifetime = DEFAULT_EXCHANGE_LIFETIME;
    private int blockwiseStatusLifetime = DEFAULT_BLOCKWISE_STATUS_LIFETIME;
    private boolean messageOffloadingEnabled = DEFAULT_MESSAGE_OFFLOADING;
    private int timeoutToAck = DEFAULT_TIMEOUT_TO_ACK;
    private boolean cidEnabled = DEFAULT_CID_ENABLED;
    private int cidLength = DEFAULT_CID_LENGTH;
    private int cidNodeId = DEFAULT_CID_NODE_ID;
    private boolean clusterEnabled = DEFAULT_CLUSTER_ENABLED;
    private int clusterPort = DEFAULT_CLUSTER_PORT;
    private String clusterBindAddress = DEFAULT_CLUSTER_BIND_ADDRESS;
    private String clusterAdvertisedAddress = null;
    private String clusterMacSecret = null;
    private Duration clusterHeartbeat = DEFAULT_CLUSTER_HEARTBEAT;
    private Duration clusterNodeTtl = DEFAULT_CLUSTER_NODE_TTL;

    /**
     * Creates properties using default values.
     */
    public CoapAdapterProperties() {
        super();
    }

    /**
     * Creates properties using existing options.
     *
     * @param options The options to copy.
     */
    public CoapAdapterProperties(final CoapAdapterOptions options) {
        super(options.adapterOptions());
        setCoapThreads(options.coapThreads());
        setConnectorThreads(options.connectorThreads());
        setDtlsRetransmissionTimeout(options.dtlsRetransmissionTimeout());
        setDtlsThreads(options.dtlsThreads());
        setExchangeLifetime(options.exchangeLifetime());
        setIdSplitRegex(options.idSplitRegex());
        this.insecureNetworkConfig = options.insecureNetworkConfig().orElse(null);
        this.messageOffloadingEnabled = options.messageOffloadingEnabled();
        this.networkConfig = options.networkConfig().orElse(null);
        this.secureNetworkConfig = options.secureNetworkConfig().orElse(null);
        setTimeoutToAck(options.timeoutToAck());
        setCidEnabled(options.cidEnabled());
        setCidLength(options.cidLength());
        setCidNodeId(options.cidNodeId());
        setClusterEnabled(options.clusterEnabled());
        setClusterPort(options.clusterPort());
        setClusterBindAddress(options.clusterBindAddress());
        setClusterAdvertisedAddress(options.clusterAdvertisedAddress().orElse(null));
        setClusterMacSecret(options.clusterMacSecret().orElse(null));
        setClusterHeartbeat(options.clusterHeartbeat());
        setClusterNodeTtl(options.clusterNodeTtl());
    }

    /**
     * Gets the regular expression used for splitting up
     * a username into the auth-id and tenant.
     * <p>
     * The default value of this property is {@link #DEFAULT_ID_SPLIT_REGEX}.
     *
     * @return The regex.
     */
    public final String getIdSplitRegex() {
        return idSplitRegex;
    }

    /**
     * Sets the regular expression to use for splitting up
     * a username into the auth-id and tenant.
     * <p>
     * The default value of this property is {@link #DEFAULT_ID_SPLIT_REGEX}.
     *
     * @param idSplitRegex The regex.
     * @throws NullPointerException if regex is {@code null}.
     */
    public final void setIdSplitRegex(final String idSplitRegex) {
        this.idSplitRegex = Objects.requireNonNull(idSplitRegex);
    }

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * all CoAP endpoints.
     *
     * @return The path.
     */
    public final String getNetworkConfig() {
        return networkConfig;
    }

    /**
     * Sets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * all CoAP endpoints..
     *
     * @param path The path to the properties file.
     */
    public final void setNetworkConfig(final String path) {
        this.networkConfig = Objects.requireNonNull(path);
    }

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>secure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     *
     * @return The path.
     */
    public final String getSecureNetworkConfig() {
        return secureNetworkConfig;
    }

    /**
     * Sets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>secure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     *
     * @param path The path.
     */
    public final void setSecureNetworkConfig(final String path) {
        this.secureNetworkConfig = Objects.requireNonNull(path);
    }

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>insecure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     *
     * @return The path.
     */
    public final String getInsecureNetworkConfig() {
        return insecureNetworkConfig;
    }

    /**
     * Sets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>insecure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     *
     * @param path The path.
     */
    public final void setInsecureNetworkConfig(final String path) {
        this.insecureNetworkConfig = Objects.requireNonNull(path);
    }

    /**
     * Gets the number of threads used for receiving/sending UDP packets.
     * <p>
     * The connector will start the given number of threads for each direction, outbound (sending)
     * as well as inbound (receiving).
     * <p>
     * The default value of this property is {@link #DEFAULT_CONNECTOR_THREADS}.
     *
     * @return The number of threads.
     */
    public final int getConnectorThreads() {
        return connectorThreads;
    }

    /**
     * Gets the number of threads to use for receiving/sending UDP packets.
     * <p>
     * The connector will start the given number of threads for each direction, outbound (sending)
     * as well as inbound (receiving).
     * <p>
     * The default value of this property is {@link #DEFAULT_CONNECTOR_THREADS}.
     *
     * @param threads The number of threads.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setConnectorThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("connector thread count must be at least 1");
        }
        this.connectorThreads = threads;
    }

    /**
     * Gets the number of threads used for processing CoAP message exchanges at the
     * protocol layer.
     * <p>
     * The default value of this property is {@link #DEFAULT_COAP_THREADS}.
     *
     * @return The number of threads.
     */
    public final int getCoapThreads() {
        return coapThreads;
    }

    /**
     * Sets the number of threads to be used for processing CoAP message exchanges at the
     * protocol layer.
     * <p>
     * The default value of this property is {@link #DEFAULT_COAP_THREADS}.
     *
     * @param threads The number of threads.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setCoapThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("protocol thread count must be at least 1");
        }
        this.coapThreads = threads;
    }

    /**
     * Gets the number of threads used for processing DTLS message exchanges at the
     * connection layer.
     * <p>
     * The default value of this property is {@link #DEFAULT_DTLS_THREADS}.
     *
     * @return The number of threads.
     */
    public final int getDtlsThreads() {
        return dtlsThreads;
    }

    /**
     * Sets the number of threads to be used for processing DTLS message exchanges at the
     * connection layer.
     * <p>
     * The default value of this property is {@link #DEFAULT_DTLS_THREADS}.
     *
     * @param threads The number of threads.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setDtlsThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("dtls thread count must be at least 1");
        }
        this.dtlsThreads = threads;
    }

    /**
     * Gets the DTLS retransmission timeout.
     * <p>
     * Timeout to wait before retransmit a flight, if no response is received.
     * <p>
     * The default value of this property is {@value #DEFAULT_DTLS_RETRANSMISSION_TIMEOUT} milliseconds.
     *
     * @return The timeout in milliseconds.
     */
    public final int getDtlsRetransmissionTimeout() {
        return dtlsRetransmissionTimeout;
    }

    /**
     * Sets the DTLS retransmission timeout.
     * <p>
     * Timeout to wait before retransmit a flight, if no response is received.
     * <p>
     * The default value of this property is {@value #DEFAULT_DTLS_RETRANSMISSION_TIMEOUT} milliseconds.
     *
     * @param dtlsRetransmissionTimeout timeout in milliseconds to retransmit a flight.
     * @throws IllegalArgumentException if dtlsRetransmissionTimeout is &lt; 1.
     */
    public final void setDtlsRetransmissionTimeout(final int dtlsRetransmissionTimeout) {
        if (dtlsRetransmissionTimeout < 1) {
            throw new IllegalArgumentException("dtls retransmission timeout must be at least 1");
        }
        this.dtlsRetransmissionTimeout = dtlsRetransmissionTimeout;
    }

    /**
     * Gets the exchange lifetime.
     * <p>
     * Time to keep coap request for deduplication.
     * <p>
     * The default value of this property is {@value #DEFAULT_EXCHANGE_LIFETIME} milliseconds.
     *
     * @return The exchange lifetime in milliseconds.
     */
    public final int getExchangeLifetime() {
        return exchangeLifetime;
    }

    /**
     * Gets the exchange lifetime.
     * <p>
     * Time to keep coap request for deduplication.
     * <p>
     * The default value of this property is {@value #DEFAULT_EXCHANGE_LIFETIME} milliseconds.
     *
     * @param exchangeLifetime the exchange lifetime in milliseconds to keep the request for deduplication.
     * @throws IllegalArgumentException if exchangeLifetime is &lt; 1.
     */
    public final void setExchangeLifetime(final int exchangeLifetime) {
        if (exchangeLifetime < 1) {
            throw new IllegalArgumentException("exchange lifetime must be at least 1");
        }
        this.exchangeLifetime = exchangeLifetime;
    }

    /**
     * Gets the blockwise status lifetime.
     * <p>
     * Time to keep a blockwise status for follow up block requests.
     * <p>
     * The default value of this property is {@value #DEFAULT_BLOCKWISE_STATUS_LIFETIME} milliseconds.
     *
     * @return The blockwise status lifetime in milliseconds.
     */
    public final int getBlockwiseStatusLifetime() {
        return blockwiseStatusLifetime;
    }

    /**
     * Gets the blockwise status lifetime.
     * <p>
     * Time to keep blockwise status for follow up block requests.
     * <p>
     * The default value of this property is {@value #DEFAULT_BLOCKWISE_STATUS_LIFETIME} milliseconds.
     *
     * @param blockwiseStatusLifetime the blockwise status lifetime in milliseconds to keep the blockwise status for
     *            follow up block requests.
     * @throws IllegalArgumentException if blockwise status lifetime is &lt; 1.
     */
    public final void setBlockwiseStatusLifetime(final int blockwiseStatusLifetime) {
        if (blockwiseStatusLifetime < 1) {
            throw new IllegalArgumentException("blockwise status lifetime must be at least 1");
        }
        this.blockwiseStatusLifetime = blockwiseStatusLifetime;
    }

    /**
     * Checks, if message offloading is enabled.
     * <p>
     * When messages are kept for deduplication, parts of the message could be offloaded to reduce the heap consumption.
     * <p>
     * The default value of this property {@value #DEFAULT_MESSAGE_OFFLOADING}.
     *
     * @return {@code true} enable message offloading, {@code false} disable message offloading.
     */
    public final boolean isMessageOffloadingEnabled() {
        return messageOffloadingEnabled;
    }

    /**
     * Sets the message offloading mode.
     * <p>
     * When messages are kept for deduplication, parts of the message could be offloaded to reduce the heap consumption.
     * <p>
     * The default value of this property is {@value #DEFAULT_MESSAGE_OFFLOADING}.
     *
     * @param messageOffloading {@code true} enable message offloading, {@code false} disable message offloading.
     */
    public final void setMessageOffloadingEnabled(final boolean messageOffloading) {
        this.messageOffloadingEnabled = messageOffloading;
    }

    /**
     * Gets the timeout to ACK a CoAP CON request.
     * <p>
     * If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout
     * is reached without response, a separate ACK is sent to prevent the client from retransmitting the CON request.
     * <p>
     * The default value of this property is {@value #DEFAULT_TIMEOUT_TO_ACK} milliseconds.
     *
     * @return The timeout in milliseconds. A value of {@code -1} means to always piggyback the response in an ACK and
     *         never send a separate CON; a value of {@code 0} means to always send an ACK immediately and include the
     *         response in a separate CON.
     */
    public final int getTimeoutToAck() {
        return timeoutToAck;
    }

    /**
     * Sets the timeout to ACK a CoAP CON request.
     * <p>
     * If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout
     * is reached without response, a separate ACK is sent to prevent the client form retransmitting the CON request.
     * <p>
     * The default value of this property is {@value #DEFAULT_TIMEOUT_TO_ACK} milliseconds.
     *
     * @param timeoutToAck timeout in milliseconds to send a separate ACK. A value of {@code -1} means to always
     *            piggyback the response in an ACK and never send a separate CON; a value of {@code 0} means to always
     *            send an ACK immediately and include the response in a separate CON.
     * @throws IllegalArgumentException if timeoutToAck is &lt; -1.
     */
    public final void setTimeoutToAck(final int timeoutToAck) {
        if (timeoutToAck < -1) {
            throw new IllegalArgumentException("timeout to ack must be at least -1");
        }
        this.timeoutToAck = timeoutToAck;
    }

    /**
     * Checks if DTLS Connection ID (CID) support is enabled.
     * <p>
     * The default value of this property is {@value #DEFAULT_CID_ENABLED}.
     *
     * @return {@code true} if CID is enabled.
     */
    public final boolean isCidEnabled() {
        return cidEnabled;
    }

    /**
     * Sets whether DTLS Connection ID (CID) support is enabled.
     * <p>
     * The default value of this property is {@value #DEFAULT_CID_ENABLED}.
     *
     * @param cidEnabled {@code true} to enable CID.
     */
    public final void setCidEnabled(final boolean cidEnabled) {
        this.cidEnabled = cidEnabled;
    }

    /**
     * Gets the length in bytes of the Connection ID.
     * <p>
     * The default value of this property is {@value #DEFAULT_CID_LENGTH}.
     *
     * @return The CID length in bytes.
     */
    public final int getCidLength() {
        return cidLength;
    }

    /**
     * Sets the length in bytes of the Connection ID.
     * <p>
     * The default value of this property is {@value #DEFAULT_CID_LENGTH}.
     * Must be between 1 and 255, and at least 5 when clustering is enabled.
     *
     * @param cidLength The CID length in bytes.
     * @throws IllegalArgumentException if cidLength &lt; 1, &gt; 255, or &lt; 5 when clustering is enabled.
     */
    public final void setCidLength(final int cidLength) {
        if (cidLength < 1 || cidLength > 255) {
            throw new IllegalArgumentException("CID length must be between 1 and 255");
        }
        if (this.clusterEnabled && cidLength < 5) {
            throw new IllegalArgumentException("CID length must be at least 5 when clustering is enabled");
        }
        this.cidLength = cidLength;
    }

    /**
     * Gets the cluster node ID embedded into Connection IDs.
     * <p>
     * The default value of this property is {@value #DEFAULT_CID_NODE_ID}.
     *
     * @return The node ID, or {@link Constants#PORT_UNCONFIGURED} if unset / auto-assigned.
     */
    public final int getCidNodeId() {
        return cidNodeId;
    }

    /**
     * Sets the cluster node ID embedded into Connection IDs.
     * <p>
     * The default value of this property is {@value #DEFAULT_CID_NODE_ID}.
     *
     * @param cidNodeId The node ID (0-255), or {@link Constants#PORT_UNCONFIGURED} for unconfigured.
     * @throws IllegalArgumentException if cidNodeId is not between 0 and 255 and not {@link Constants#PORT_UNCONFIGURED}.
     */
    public final void setCidNodeId(final int cidNodeId) {
        if (cidNodeId != Constants.PORT_UNCONFIGURED && (cidNodeId < 0 || cidNodeId > 255)) {
            throw new IllegalArgumentException("CID node ID must be between 0 and 255, or " + Constants.PORT_UNCONFIGURED);
        }
        this.cidNodeId = cidNodeId;
    }

    /**
     * Checks if DTLS cluster mesh forwarding is enabled.
     * <p>
     * The default value of this property is {@value #DEFAULT_CLUSTER_ENABLED}.
     *
     * @return {@code true} if clustering is enabled.
     */
    public final boolean isClusterEnabled() {
        return clusterEnabled;
    }

    /**
     * Sets whether DTLS cluster mesh forwarding is enabled.
     * <p>
     * The default value of this property is {@value #DEFAULT_CLUSTER_ENABLED}.
     *
     * @param clusterEnabled {@code true} to enable clustering.
     * @throws IllegalArgumentException if clusterEnabled is true and cidLength &lt; 5.
     */
    public final void setClusterEnabled(final boolean clusterEnabled) {
        if (clusterEnabled && this.cidLength < 5) {
            throw new IllegalArgumentException("CID length must be at least 5 when clustering is enabled");
        }
        this.clusterEnabled = clusterEnabled;
    }

    /**
     * Gets the port used for cluster mesh forwarding.
     * <p>
     * The default value of this property is {@value #DEFAULT_CLUSTER_PORT}.
     *
     * @return The cluster port.
     */
    public final int getClusterPort() {
        return clusterPort;
    }

    /**
     * Sets the port used for cluster mesh forwarding.
     * <p>
     * The default value of this property is {@value #DEFAULT_CLUSTER_PORT}.
     *
     * @param clusterPort The port number (1-65535).
     * @throws IllegalArgumentException if clusterPort &lt;= 0 or &gt; 65535.
     */
    public final void setClusterPort(final int clusterPort) {
        if (clusterPort <= 0 || clusterPort > 65535) {
            throw new IllegalArgumentException("cluster port must be between 1 and 65535");
        }
        this.clusterPort = clusterPort;
    }

    /**
     * Gets the bind address for the cluster connector.
     * <p>
     * The default value of this property is {@value #DEFAULT_CLUSTER_BIND_ADDRESS}.
     *
     * @return The bind address.
     */
    public final String getClusterBindAddress() {
        return clusterBindAddress;
    }

    /**
     * Sets the bind address for the cluster connector.
     * <p>
     * The default value of this property is {@value #DEFAULT_CLUSTER_BIND_ADDRESS}.
     *
     * @param clusterBindAddress The bind address.
     * @throws NullPointerException if clusterBindAddress is {@code null}.
     */
    public final void setClusterBindAddress(final String clusterBindAddress) {
        this.clusterBindAddress = Objects.requireNonNull(clusterBindAddress);
    }

    /**
     * Gets the IP address or host name that other cluster nodes use for reaching the cluster connector.
     * <p>
     * In a Kubernetes cluster, this is usually the IP address of the pod that the adapter runs in.
     *
     * @return The address or {@code null} if the bind address should be used.
     */
    public final String getClusterAdvertisedAddress() {
        return clusterAdvertisedAddress;
    }

    /**
     * Sets the IP address or host name that other cluster nodes use for reaching the cluster connector.
     * <p>
     * The address needs to be set if the cluster connector is bound to a wildcard address.
     *
     * @param clusterAdvertisedAddress The address or {@code null} if the bind address should be used.
     */
    public final void setClusterAdvertisedAddress(final String clusterAdvertisedAddress) {
        this.clusterAdvertisedAddress = clusterAdvertisedAddress;
    }

    /**
     * Gets the shared secret for authenticating cluster forward messages.
     *
     * @return The MAC secret, or {@code null} if not configured.
     */
    public final String getClusterMacSecret() {
        return clusterMacSecret;
    }

    /**
     * Sets the shared secret for authenticating cluster forward messages.
     *
     * @param clusterMacSecret The MAC secret, or {@code null} to disable MAC.
     */
    public final void setClusterMacSecret(final String clusterMacSecret) {
        this.clusterMacSecret = clusterMacSecret;
    }

    /**
     * Gets the interval between cluster node heartbeat registrations.
     * <p>
     * The default value of this property is 10 seconds.
     *
     * @return The heartbeat interval.
     */
    public final Duration getClusterHeartbeat() {
        return clusterHeartbeat;
    }

    /**
     * Sets the interval between cluster node heartbeat registrations.
     *
     * @param clusterHeartbeat The heartbeat interval.
     * @throws NullPointerException if clusterHeartbeat is {@code null}.
     * @throws IllegalArgumentException if clusterHeartbeat is not positive.
     */
    public final void setClusterHeartbeat(final Duration clusterHeartbeat) {
        Objects.requireNonNull(clusterHeartbeat);
        if (clusterHeartbeat.isNegative() || clusterHeartbeat.isZero()) {
            throw new IllegalArgumentException("cluster heartbeat must be positive");
        }
        this.clusterHeartbeat = clusterHeartbeat;
    }

    /**
     * Sets the interval between cluster node heartbeat registrations in milliseconds.
     *
     * @param millis The heartbeat interval in milliseconds.
     * @throws IllegalArgumentException if millis &lt;= 0.
     */
    public final void setClusterHeartbeat(final long millis) {
        if (millis <= 0) {
            throw new IllegalArgumentException("cluster heartbeat must be positive");
        }
        this.clusterHeartbeat = Duration.ofMillis(millis);
    }

    /**
     * Gets the time-to-live for node entries in the cluster registry.
     * <p>
     * The default value of this property is 30 seconds.
     *
     * @return The node TTL.
     */
    public final Duration getClusterNodeTtl() {
        return clusterNodeTtl;
    }

    /**
     * Sets the time-to-live for node entries in the cluster registry.
     *
     * @param clusterNodeTtl The node TTL.
     * @throws NullPointerException if clusterNodeTtl is {@code null}.
     * @throws IllegalArgumentException if clusterNodeTtl is not positive.
     */
    public final void setClusterNodeTtl(final Duration clusterNodeTtl) {
        Objects.requireNonNull(clusterNodeTtl);
        if (clusterNodeTtl.isNegative() || clusterNodeTtl.isZero()) {
            throw new IllegalArgumentException("cluster node TTL must be positive");
        }
        this.clusterNodeTtl = clusterNodeTtl;
    }

    /**
     * Sets the time-to-live for node entries in the cluster registry in milliseconds.
     *
     * @param millis The node TTL in milliseconds.
     * @throws IllegalArgumentException if millis &lt;= 0.
     */
    public final void setClusterNodeTtl(final long millis) {
        if (millis <= 0) {
            throw new IllegalArgumentException("cluster node TTL must be positive");
        }
        this.clusterNodeTtl = Duration.ofMillis(millis);
    }
}

