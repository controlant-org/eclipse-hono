/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.redis.config;

import io.smallrye.config.RelocateConfigSourceInterceptor;

/**
 * A config source interceptor that relocates the Quarkus Redis client's configuration properties
 * to Hono's Command Router cache namespace.
 * <p>
 * The Quarkus Redis extension reads its configuration from properties prefixed with {@code quarkus.redis}.
 * This interceptor relocates every such lookup to the corresponding {@code hono.commandRouter.cache.redis}
 * property, so that the Redis connection can be configured using the same {@code hono.commandRouter.cache}
 * naming convention that the Infinispan variant of the Command Router uses (see the
 * <em>Data Grid Connection Configuration</em> section of the Command Router admin guide). For example,
 * setting {@code hono.commandRouter.cache.redis.hosts} provides the value that the Quarkus Redis extension
 * reads for {@code quarkus.redis.hosts}.
 * <p>
 * The interceptor is registered via {@code META-INF/services/io.smallrye.config.ConfigSourceInterceptor}.
 */
public class RedisConfigInterceptor extends RelocateConfigSourceInterceptor {

    /**
     * Creates a new interceptor that relocates {@code quarkus.redis} properties to
     * {@code hono.commandRouter.cache.redis}.
     */
    public RedisConfigInterceptor() {
        super(name -> name.startsWith("quarkus.redis") ?
                    name.replaceAll("quarkus\\.redis", "hono.commandRouter.cache.redis") : name);
    }
}
