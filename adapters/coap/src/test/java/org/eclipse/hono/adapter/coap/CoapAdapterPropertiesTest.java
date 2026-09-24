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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link CoapAdapterProperties}.
 */
public class CoapAdapterPropertiesTest {

    /**
     * Verifies default values for DTLS CID and cluster properties.
     */
    @Test
    void testDefaultCidAndClusterProperties() {
        final CoapAdapterProperties props = new CoapAdapterProperties();
        assertFalse(props.isCidEnabled());
        assertEquals(6, props.getCidLength());
        assertEquals(Constants.PORT_UNCONFIGURED, props.getCidNodeId());
        assertFalse(props.isClusterEnabled());
        assertEquals(5685, props.getClusterPort());
        assertEquals("0.0.0.0", props.getClusterBindAddress());
        assertNull(props.getClusterMacSecret());
        assertEquals(Duration.ofMillis(10_000), props.getClusterHeartbeat());
        assertEquals(Duration.ofMillis(30_000), props.getClusterNodeTtl());
        assertTrue(props.isSessionResumptionEnabled());
    }

    /**
     * Verifies that valid CID and cluster properties can be set and retrieved.
     */
    @Test
    void testSetCidAndClusterProperties() {
        final CoapAdapterProperties props = new CoapAdapterProperties();

        props.setCidEnabled(true);
        assertTrue(props.isCidEnabled());

        props.setCidLength(8);
        assertEquals(8, props.getCidLength());

        props.setCidNodeId(42);
        assertEquals(42, props.getCidNodeId());

        props.setClusterEnabled(true);
        assertTrue(props.isClusterEnabled());

        props.setClusterPort(5686);
        assertEquals(5686, props.getClusterPort());

        props.setClusterBindAddress("127.0.0.1");
        assertEquals("127.0.0.1", props.getClusterBindAddress());

        props.setClusterMacSecret("my-secret");
        assertEquals("my-secret", props.getClusterMacSecret());

        props.setClusterHeartbeat(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), props.getClusterHeartbeat());

        props.setClusterHeartbeat(8_000);
        assertEquals(Duration.ofMillis(8_000), props.getClusterHeartbeat());

        props.setClusterNodeTtl(Duration.ofSeconds(20));
        assertEquals(Duration.ofSeconds(20), props.getClusterNodeTtl());

        props.setClusterNodeTtl(25_000);
        assertEquals(Duration.ofMillis(25_000), props.getClusterNodeTtl());

        props.setSessionResumptionEnabled(false);
        assertFalse(props.isSessionResumptionEnabled());
    }

    /**
     * Verifies validation of CID length, cluster port, and cluster parameters.
     */
    @Test
    void testValidation() {
        final CoapAdapterProperties props = new CoapAdapterProperties();

        // CID length >= 1 and <= 255
        assertThrows(IllegalArgumentException.class, () -> props.setCidLength(0));
        assertThrows(IllegalArgumentException.class, () -> props.setCidLength(-1));
        assertThrows(IllegalArgumentException.class, () -> props.setCidLength(256));

        // CID length >= 5 if clusterEnabled
        props.setClusterEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> props.setCidLength(4));
        props.setCidLength(5); // valid
        assertEquals(5, props.getCidLength());

        // Enabling cluster with CID length < 5 should fail
        props.setClusterEnabled(false);
        props.setCidLength(4);
        assertThrows(IllegalArgumentException.class, () -> props.setClusterEnabled(true));

        // CID node ID (0-255 or -1)
        assertThrows(IllegalArgumentException.class, () -> props.setCidNodeId(-2));
        assertThrows(IllegalArgumentException.class, () -> props.setCidNodeId(256));
        props.setCidNodeId(Constants.PORT_UNCONFIGURED);
        assertEquals(Constants.PORT_UNCONFIGURED, props.getCidNodeId());
        props.setCidNodeId(0);
        assertEquals(0, props.getCidNodeId());
        props.setCidNodeId(255);
        assertEquals(255, props.getCidNodeId());

        // Cluster port > 0 and <= 65535
        assertThrows(IllegalArgumentException.class, () -> props.setClusterPort(0));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterPort(-1));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterPort(65536));

        // Null checks
        assertThrows(NullPointerException.class, () -> props.setClusterBindAddress(null));
        assertThrows(NullPointerException.class, () -> props.setClusterHeartbeat(null));
        assertThrows(NullPointerException.class, () -> props.setClusterNodeTtl(null));

        // Non-positive durations
        assertThrows(IllegalArgumentException.class, () -> props.setClusterHeartbeat(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterHeartbeat(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterHeartbeat(0));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterHeartbeat(-100));

        assertThrows(IllegalArgumentException.class, () -> props.setClusterNodeTtl(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterNodeTtl(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterNodeTtl(0));
        assertThrows(IllegalArgumentException.class, () -> props.setClusterNodeTtl(-100));
    }

    /**
     * Verifies that CoapAdapterOptions maps default configuration and binds to CoapAdapterProperties.
     */
    @Test
    void testDefaultCoapAdapterOptionsBindingFromYaml() {
        final CoapAdapterOptions options = ConfigMappingSupport.getConfigMapping(
                CoapAdapterOptions.class,
                this.getClass().getResource("/coap-adapter-options-default.yaml"));

        assertFalse(options.cidEnabled());
        assertEquals(6, options.cidLength());
        assertEquals(Constants.PORT_UNCONFIGURED, options.cidNodeId());
        assertFalse(options.clusterEnabled());
        assertEquals(5685, options.clusterPort());
        assertEquals("0.0.0.0", options.clusterBindAddress());
        assertTrue(options.clusterMacSecret().isEmpty());
        assertEquals(Duration.ofSeconds(10), options.clusterHeartbeat());
        assertEquals(Duration.ofSeconds(30), options.clusterNodeTtl());
        assertTrue(options.sessionResumptionEnabled());

        final CoapAdapterProperties props = new CoapAdapterProperties(options);
        assertFalse(props.isCidEnabled());
        assertEquals(6, props.getCidLength());
        assertEquals(Constants.PORT_UNCONFIGURED, props.getCidNodeId());
        assertFalse(props.isClusterEnabled());
        assertEquals(5685, props.getClusterPort());
        assertEquals("0.0.0.0", props.getClusterBindAddress());
        assertNull(props.getClusterMacSecret());
        assertEquals(Duration.ofSeconds(10), props.getClusterHeartbeat());
        assertEquals(Duration.ofSeconds(30), props.getClusterNodeTtl());
        assertTrue(props.isSessionResumptionEnabled());
    }

    /**
     * Verifies that CoapAdapterOptions maps YAML configuration and binds to CoapAdapterProperties.
     */
    @Test
    void testCoapAdapterOptionsBindingFromYaml() {
        final CoapAdapterOptions options = ConfigMappingSupport.getConfigMapping(
                CoapAdapterOptions.class,
                this.getClass().getResource("/coap-adapter-options.yaml"));

        assertTrue(options.cidEnabled());
        assertEquals(8, options.cidLength());
        assertEquals(15, options.cidNodeId());
        assertTrue(options.clusterEnabled());
        assertEquals(5686, options.clusterPort());
        assertEquals("192.168.1.1", options.clusterBindAddress());
        assertEquals(Optional.of("secret-key"), options.clusterMacSecret());
        assertEquals(Duration.ofSeconds(12), options.clusterHeartbeat());
        assertEquals(Duration.ofSeconds(35), options.clusterNodeTtl());
        assertFalse(options.sessionResumptionEnabled());

        final CoapAdapterProperties props = new CoapAdapterProperties(options);
        assertTrue(props.isCidEnabled());
        assertEquals(8, props.getCidLength());
        assertEquals(15, props.getCidNodeId());
        assertTrue(props.isClusterEnabled());
        assertEquals(5686, props.getClusterPort());
        assertEquals("192.168.1.1", props.getClusterBindAddress());
        assertEquals("secret-key", props.getClusterMacSecret());
        assertEquals(Duration.ofSeconds(12), props.getClusterHeartbeat());
        assertEquals(Duration.ofSeconds(35), props.getClusterNodeTtl());
        assertFalse(props.isSessionResumptionEnabled());
    }
}
