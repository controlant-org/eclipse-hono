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

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.californium.scandium.DtlsClusterConnector;
import org.eclipse.californium.scandium.DtlsManagedClusterConnector;
import org.eclipse.californium.scandium.config.DtlsClusterConnectorConfig;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A DTLS cluster connector that authenticates the records it exchanges with other cluster nodes
 * by means of a MAC that is based on a secret shared by all cluster nodes.
 * <p>
 * Californium's {@link DtlsClusterConnector} exchanges forwarded (incoming) and backwarded (outgoing)
 * records with other cluster nodes without any authentication. Anybody who can reach the cluster port
 * could therefore inject records with spoofed device addresses, or make the connector send arbitrary
 * datagrams from its public socket to arbitrary destinations. Californium's
 * {@link DtlsManagedClusterConnector} supports MACs, but derives the MAC keys from DTLS connections
 * between the cluster nodes that need to be established by additional cluster management.
 * <p>
 * This connector instead uses a static key for calculating the MACs, using the same record format and
 * MAC calculation as {@link DtlsManagedClusterConnector}. The MAC covers the record type, the device
 * address and the first and last 32 bytes of the record. Records with an invalid MAC are dropped.
 */
public class MacProtectedDtlsClusterConnector extends DtlsClusterConnector {

    /**
     * The algorithm used for calculating MACs.
     */
    public static final String MAC_ALGORITHM = "HmacSHA256";

    /**
     * The minimum length of the shared secret in bytes.
     */
    public static final int MIN_SECRET_LENGTH = 16;

    private static final Logger LOG = LoggerFactory.getLogger(MacProtectedDtlsClusterConnector.class);

    private final ThreadLocal<Mac> mac;

    /**
     * Creates a new connector.
     *
     * @param configuration The DTLS configuration.
     * @param clusterConfiguration The configuration of the cluster internal connector.
     * @param nodesProvider The provider of the other cluster nodes' addresses.
     * @param macKey The key to use for calculating MACs.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the key cannot be used for calculating MACs or if the
     *                                  configuration does not support a multi-node connection ID generator.
     */
    public MacProtectedDtlsClusterConnector(
            final DtlsConnectorConfig configuration,
            final DtlsClusterConnectorConfig clusterConfiguration,
            final ClusterNodesProvider nodesProvider,
            final SecretKey macKey) {
        super(configuration, clusterConfiguration, Objects.requireNonNull(nodesProvider));
        Objects.requireNonNull(macKey);
        // fail early if the key cannot be used
        createMac(macKey);
        this.mac = ThreadLocal.withInitial(() -> createMac(macKey));
    }

    /**
     * Creates the key for calculating MACs from a shared secret.
     *
     * @param secret The secret shared by all cluster nodes.
     * @return The key.
     * @throws NullPointerException if the secret is {@code null}.
     * @throws IllegalArgumentException if the secret is shorter than {@value #MIN_SECRET_LENGTH} bytes.
     */
    public static SecretKey createMacKey(final String secret) {
        Objects.requireNonNull(secret);
        final byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(String.format(
                    "cluster MAC secret must have a length of at least %d bytes", MIN_SECRET_LENGTH));
        }
        return new SecretKeySpec(secretBytes, MAC_ALGORITHM);
    }

    private static Mac createMac(final SecretKey key) {
        try {
            final Mac result = Mac.getInstance(MAC_ALGORITHM);
            result.init(key);
            return result;
        } catch (final GeneralSecurityException e) {
            throw new IllegalArgumentException("cannot create cluster MAC", e);
        }
    }

    @Override
    protected int getClusterMacLength() {
        return CLUSTER_MAC_LENGTH;
    }

    @Override
    protected void sendDatagramToClusterNetwork(final DatagramPacket clusterPacket) throws IOException {
        DtlsManagedClusterConnector.setClusterMac(mac.get(), clusterPacket);
        super.sendDatagramToClusterNetwork(clusterPacket);
    }

    @Override
    protected void processDatagramFromClusterNetwork(final Byte type, final DatagramPacket clusterPacket)
            throws IOException {
        if (!hasValidMac(clusterPacket)) {
            LOG.debug("cluster-node {}: dropping cluster record with invalid MAC from {}",
                    getNodeID(), clusterPacket.getSocketAddress());
            if (clusterHealth != null) {
                if (RECORD_TYPE_INCOMING.equals(type)) {
                    clusterHealth.badForwardMessage();
                } else if (RECORD_TYPE_OUTGOING.equals(type)) {
                    clusterHealth.badBackwardMessage();
                }
            }
            return;
        }
        super.processDatagramFromClusterNetwork(type, clusterPacket);
    }

    private boolean hasValidMac(final DatagramPacket clusterPacket) {
        try {
            return DtlsManagedClusterConnector.validateClusterMac(mac.get(), clusterPacket);
        } catch (final IllegalArgumentException e) {
            // record is too short for containing a MAC
            return false;
        }
    }
}
