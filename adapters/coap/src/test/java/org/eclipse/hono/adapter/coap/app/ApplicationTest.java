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

package org.eclipse.hono.adapter.coap.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.eclipse.hono.service.ApplicationConfigProperties;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link Application}.
 */
public class ApplicationTest {

    /**
     * Verifies that the default of one verticle instance per CPU core is reduced to a single instance.
     */
    @Test
    void testLimitToSingleAdapterInstanceOverridesDefault() {
        final ApplicationConfigProperties appConfig = new ApplicationConfigProperties();

        Application.limitToSingleAdapterInstance(appConfig);

        assertEquals(1, appConfig.getMaxInstances());
    }

    /**
     * Verifies that an explicitly configured number of verticle instances is reduced to a single instance.
     */
    @Test
    void testLimitToSingleAdapterInstanceOverridesExplicitValue() {
        final ApplicationConfigProperties appConfig = new ApplicationConfigProperties();
        appConfig.setMaxInstances(4);

        Application.limitToSingleAdapterInstance(appConfig);

        assertEquals(1, appConfig.getMaxInstances());
    }

    /**
     * Verifies that an explicitly configured node ID takes precedence over the StatefulSet pod ordinal.
     */
    @Test
    void testResolveCidNodeIdUsesConfiguredNodeId() {
        assertEquals(7, Application.resolveCidNodeId(7, Optional.of("hono-adapter-coap-3")));
        assertEquals(0, Application.resolveCidNodeId(0, Optional.empty()));
    }

    /**
     * Verifies that the StatefulSet pod ordinal is used as the node ID if no node ID has been configured.
     */
    @Test
    void testResolveCidNodeIdUsesStatefulSetOrdinal() {
        assertEquals(3, Application.resolveCidNodeId(-1, Optional.of("hono-adapter-coap-3")));
        assertEquals(0, Application.resolveCidNodeId(-1, Optional.of("coap-0")));
        assertEquals(255, Application.resolveCidNodeId(-1, Optional.of("coap-255")));
    }

    /**
     * Verifies that resolving the node ID fails if it can neither be taken from the configuration
     * nor from the host name.
     */
    @Test
    void testResolveCidNodeIdFailsWithoutUsableHostName() {
        assertThrows(IllegalStateException.class, () -> Application.resolveCidNodeId(-1, Optional.empty()));
        // a Deployment's pod names end with a random suffix
        assertThrows(IllegalStateException.class,
                () -> Application.resolveCidNodeId(-1, Optional.of("hono-adapter-coap-7d9f8c6b5d-x2x9z")));
        assertThrows(IllegalStateException.class, () -> Application.resolveCidNodeId(-1, Optional.of("coap-256")));
    }
}
