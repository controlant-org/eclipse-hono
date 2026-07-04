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

package org.eclipse.hono.deviceconnection.redis.client;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

/**
 * Tests verifying the behavior of {@link RedisCache} against a real Redis server.
 * <p>
 * The Redis client is configured with a connection pool of size 1 so that every
 * operation reuses the same physical connection. Any operation that leaks
 * connection-level state (an armed {@code WATCH} or an open {@code MULTI})
 * therefore corrupts subsequent operations and fails the suite.
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
class RedisCacheTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private Redis redisClient;
    private RedisAPI api;
    private RedisCache cache;

    @BeforeEach
    void setUp(final Vertx vertx) {
        redisClient = Redis.createClient(vertx, new RedisOptions()
                .setConnectionString("redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)))
                .setMaxPoolSize(1)
                .setMaxPoolWaiting(32));
        api = RedisAPI.api(redisClient);
        cache = RedisCache.from(api, redisClient);
    }

    @AfterEach
    void tearDown() {
        redisClient.close();
    }

    private static String randomKey(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /**
     * Verifies that a value that has been put can be retrieved again.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAndGetSucceeds(final VertxTestContext ctx) {
        final String key = randomKey("put-get");
        cache.put(key, "the-value")
                .compose(ok -> cache.get(key))
                .onComplete(ctx.succeeding(value -> {
                    ctx.verify(() -> assertThat(value).isEqualTo("the-value"));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that values stored via putAll can be retrieved via getAll.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testPutAllAndGetAllSucceeds(final VertxTestContext ctx) {
        final String keyA = randomKey("all-a");
        final String keyB = randomKey("all-b");
        cache.putAll(Map.of(keyA, "value-a", keyB, "value-b"))
                .compose(ok -> cache.getAll(Set.of(keyA, keyB)))
                .onComplete(ctx.succeeding(values -> {
                    ctx.verify(() -> {
                        assertThat(values).containsEntry(keyA, "value-a");
                        assertThat(values).containsEntry(keyB, "value-b");
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that getAll omits keys that do not exist, matching the behavior
     * of the Infinispan based BasicCache.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetAllOmitsMissingKeys(final VertxTestContext ctx) {
        final String present = randomKey("present");
        final String missing = randomKey("missing");
        cache.put(present, "here")
                .compose(ok -> cache.getAll(Set.of(present, missing)))
                .onComplete(ctx.succeeding(values -> {
                    ctx.verify(() -> {
                        assertThat(values).containsEntry(present, "here");
                        assertThat(values).doesNotContainKey(missing);
                        assertThat(values).hasSize(1);
                    });
                    ctx.completeNow();
                }));
    }
}
