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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Tests verifying the Redis configuration support of the CoAP adapter.
 */
public class CacheProducerTest {

    private static final String PLACEHOLDER = "redis://" + CacheProducer.UNCONFIGURED_REDIS_HOST + ":6379";

    private static SmallRyeConfig config(final Map<String, String> defaults, final Map<String, String> overrides) {
        return new SmallRyeConfigBuilder()
                .withInterceptors(new RedisConfigInterceptor())
                .withSources(new PropertiesConfigSource(defaults, "defaults", 250))
                .withSources(new PropertiesConfigSource(overrides, "overrides", 300))
                .build();
    }

    /**
     * Verifies that the Quarkus Redis client reads its hosts from the CoAP adapter's cache namespace.
     */
    @Test
    void testQuarkusRedisPropertiesAreRelocated() {
        final SmallRyeConfig config = config(
                Map.of("quarkus.redis.hosts", PLACEHOLDER),
                Map.of("hono.cache.redis.hosts", "redis://redis:6379"));

        assertEquals("redis://redis:6379", config.getRawValue("quarkus.redis.hosts"));
    }

    /**
     * Verifies that the check succeeds if the Redis hosts have been configured explicitly.
     */
    @Test
    void testCheckRedisHostsConfiguredSucceedsForExplicitHosts() {
        final SmallRyeConfig config = config(
                Map.of("quarkus.redis.hosts", PLACEHOLDER),
                Map.of("hono.cache.redis.hosts", "redis://redis:6379"));

        assertDoesNotThrow(() -> CacheProducer.checkRedisHostsConfigured(config));
    }

    /**
     * Verifies that the check fails if only the default placeholder host is configured.
     */
    @Test
    void testCheckRedisHostsConfiguredFailsForPlaceholder() {
        final SmallRyeConfig config = config(Map.of("quarkus.redis.hosts", PLACEHOLDER), Map.of());

        assertThrows(IllegalStateException.class, () -> CacheProducer.checkRedisHostsConfigured(config));
    }

    /**
     * Verifies that the check fails if no Redis hosts are configured at all.
     */
    @Test
    void testCheckRedisHostsConfiguredFailsIfUnset() {
        final SmallRyeConfig config = config(Map.of(), Map.of());

        assertThrows(IllegalStateException.class, () -> CacheProducer.checkRedisHostsConfigured(config));
    }
}
