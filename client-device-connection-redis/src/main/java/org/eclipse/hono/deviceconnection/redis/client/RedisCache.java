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

package org.eclipse.hono.deviceconnection.redis.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;

/**
 * TODO.
 */
public class RedisCache implements Cache<String, String>, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCache.class);

    private final RedisAPI api;
    private final Redis redisClient;

    /**
     * TODO.
     *
     * @param api TODO.
     * @param redisClient TODO.
     */
    private RedisCache(final RedisAPI api, final Redis redisClient) {
        Objects.requireNonNull(api);
        Objects.requireNonNull(redisClient);
        this.api = api;
        this.redisClient = redisClient;
    }

    /**
     * TODO.
     *
     * @param api TODO.
     * @param redisClient TODO.
     * @return TODO.
     */
    public static RedisCache from(final RedisAPI api, final Redis redisClient) {
        Objects.requireNonNull(api);
        Objects.requireNonNull(redisClient);
        return new RedisCache(api, redisClient);
    }

    @Override
    public Future<Void> start() {
        return checkForCacheAvailability().mapEmpty();
    }

    @Override
    public Future<Void> stop() {
        return Future.succeededFuture();
    }

    @Override
    public Future<JsonObject> checkForCacheAvailability() {
        return api.ping(List.of())
                .map(new JsonObject());
    }

    @Override
    public Future<Void> put(final String key, final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return api.set(List.of(key, value))
                .mapEmpty();
    }

    @Override
    public Future<Void> put(final String key, final String value, final long lifespan, final TimeUnit lifespanUnit) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(lifespanUnit);

        final List<String> params = new ArrayList<>(List.of(key, value));
        final long millis = lifespanUnit.toMillis(lifespan);
        if (millis > 0) {
            params.addAll(List.of("PX", String.valueOf(millis)));
        }
        return api.set(params)
                .mapEmpty();
    }

    @Override
    public Future<Void> putAll(final Map<? extends String, ? extends String> data) {
        Objects.requireNonNull(data);

        final List<String> keyValues = new ArrayList<>(data.size() * 2);
        data.forEach((k, v) -> {
            keyValues.add(k);
            keyValues.add(v);
        });
        return api.mset(keyValues)
                .mapEmpty();
    }

    @Override
    public Future<Void> putAll(final Map<? extends String, ? extends String> data, final long lifespan,
            final TimeUnit lifespanUnit) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(lifespanUnit);

        final long millis = lifespanUnit.toMillis(lifespan);
        return redisClient.connect()
                .compose(conn -> {
                    final RedisAPI connApi = RedisAPI.api(conn);
                    return connApi.multi()
                            .compose(ignored -> {
                                final List<Future<Response>> futures = new ArrayList<>(data.size());
                                data.forEach((k, v) -> {
                                    final List<String> params = new ArrayList<>(List.of(String.valueOf(k), String.valueOf(v)));
                                    if (millis > 0) {
                                        params.addAll(List.of("PX", String.valueOf(millis)));
                                    }
                                    futures.add(connApi.set(params));
                                });
                                return Future.all(Collections.unmodifiableList(futures));
                            })
                            .compose(ignored -> connApi.exec())
                            // null reply means transaction aborted
                            .map(Objects::nonNull)
                            .eventually(() -> conn.close())
                            .mapEmpty();
                });
    }

    @Override
    public Future<String> get(final String key) {
        Objects.requireNonNull(key);

        return api.get(key)
                .map(value -> value == null ? null : value.toString(StandardCharsets.UTF_8));
    }

    @Override
    public Future<Boolean> remove(final String key, final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return redisClient.connect()
                .compose(conn -> {
                    final RedisAPI connApi = RedisAPI.api(conn);
                    return connApi.watch(List.of(key))
                            .compose(ignored -> connApi.get(key))
                            .compose(response -> {
                                if (response == null) {
                                    // key does not exist
                                    return Future.succeededFuture(false);
                                }
                                if (String.valueOf(response).equals(value)) {
                                    return connApi.multi()
                                            .compose(ignored -> connApi.del(List.of(key)))
                                            .compose(ignored -> connApi.exec())
                                            // null reply means transaction aborted
                                            .map(Objects::nonNull);
                                } else {
                                    return Future.succeededFuture(false);
                                }
                            })
                            .eventually(() -> conn.close());
                });
    }

    @Override
    public Future<Map<String, String>> getAll(final Set<? extends String> keys) {
        Objects.requireNonNull(keys);

        final LinkedList<String> keyList = new LinkedList<>(keys.stream().map(String::valueOf).toList());
        final Map<String, String> result = new HashMap<>(keyList.size());
        return api.mget(keyList)
                .compose(values -> {
                    values.forEach(i -> {
                        if (i != null) { // TODO: this is kinda strange but some results are null and the BasicCache does not include those in the returned result. Ask about/investigate.
                            result.put(keyList.removeFirst(), i.toString());
                        } else {
                            keyList.removeFirst();
                        }
                    });
                    return Future.succeededFuture(result);
                });
    }
}
