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

import java.util.Objects;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.eclipse.hono.deviceconnection.redis.client.RedisCache;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.redis.client.RedisAPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A producer of the Redis based cache that the CoAP adapter uses to share state with other
 * adapter instances when cluster mode is enabled.
 */
@ApplicationScoped
public class CacheProducer {

    /**
     * The host name of the placeholder Redis URI that is configured by default.
     * <p>
     * The placeholder is required because the Quarkus Redis extension refuses to start
     * without a configured host, even if the client is never used.
     */
    static final String UNCONFIGURED_REDIS_HOST = "hono-coap-cache-not-configured";

    private static final Logger LOG = LoggerFactory.getLogger(CacheProducer.class);
    private static final String PROPERTY_REDIS_HOSTS = "quarkus.redis.hosts";

    /**
     * Creates the cache for sharing cluster state.
     *
     * @param redisAPI The Redis client to use for accessing the cache.
     * @return The cache.
     */
    @Produces
    @Singleton
    Cache<String, String> cache(final RedisAPI redisAPI) {
        LOG.info("configuring Redis cache");
        return RedisCache.from(redisAPI);
    }

    /**
     * Verifies that the Redis hosts to connect to have been configured explicitly.
     *
     * @param config The configuration to check.
     * @throws NullPointerException if config is {@code null}.
     * @throws IllegalStateException if no hosts or only the default placeholder host have been configured.
     */
    static void checkRedisHostsConfigured(final Config config) {
        Objects.requireNonNull(config);
        final String hosts = config.getOptionalValue(PROPERTY_REDIS_HOSTS, String.class).orElse("");
        if (hosts.isBlank() || hosts.contains(UNCONFIGURED_REDIS_HOST)) {
            throw new IllegalStateException(
                    "cluster mode requires a Redis cache, the hono.cache.redis.hosts property must be set");
        }
    }
}
