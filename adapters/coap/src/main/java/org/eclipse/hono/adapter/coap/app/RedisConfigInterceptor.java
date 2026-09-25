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

import io.smallrye.config.RelocateConfigSourceInterceptor;

/**
 * A config source interceptor that relocates the Quarkus Redis client's configuration properties
 * to the CoAP adapter's cache namespace.
 * <p>
 * The namespace is deliberately not nested below {@code hono.coap}, because the CoAP adapter options
 * mapping rejects any property of that prefix that it does not know about.
 * <p>
 * The Quarkus Redis extension reads its configuration from properties prefixed with {@code quarkus.redis}.
 * This interceptor relocates every such lookup to the corresponding {@code hono.cache.redis}
 * property. For example, setting {@code hono.cache.redis.hosts} provides the value that the Quarkus
 * Redis extension reads for {@code quarkus.redis.hosts}. A value set for the original property name is
 * used if the relocated property is not set or has been defined in a config source of lower priority.
 * <p>
 * The interceptor is registered via {@code META-INF/services/io.smallrye.config.ConfigSourceInterceptor}.
 */
public class RedisConfigInterceptor extends RelocateConfigSourceInterceptor {

    static final String QUARKUS_REDIS_PREFIX = "quarkus.redis.";
    static final String HONO_REDIS_PREFIX = "hono.cache.redis.";

    /**
     * Creates a new interceptor that relocates {@code quarkus.redis} properties to
     * {@code hono.cache.redis}.
     */
    public RedisConfigInterceptor() {
        super(name -> name.startsWith(QUARKUS_REDIS_PREFIX)
                ? HONO_REDIS_PREFIX + name.substring(QUARKUS_REDIS_PREFIX.length())
                : name);
    }
}
