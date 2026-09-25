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
}
