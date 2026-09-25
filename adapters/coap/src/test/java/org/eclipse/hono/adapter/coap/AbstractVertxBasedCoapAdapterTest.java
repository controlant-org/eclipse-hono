/**
 * Copyright (c) 2018, 2026 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.hono.adapter.coap.cluster.CacheBasedClusterNodesProvider;
import org.eclipse.hono.adapter.coap.cluster.CoapClusterNodeRegistry;
import org.eclipse.hono.adapter.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.adapter.test.ProtocolAdapterTestSupport;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link AbstractVertxBasedCoapAdapter}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class AbstractVertxBasedCoapAdapterTest extends ProtocolAdapterTestSupport<CoapAdapterProperties, AbstractVertxBasedCoapAdapter<CoapAdapterProperties>> {

    private static final Vertx vertx = Vertx.vertx();

    private ProtocolAdapterCommandConsumer commandConsumer;
    private ResourceLimitChecks resourceLimitChecks;
    private CoapAdapterMetrics metrics;
    private CoapServer server;
    private Handler<Void> startupHandler;
    private Runnable preShutdownAction;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setup() {

        startupHandler = VertxMockSupport.mockHandler();
        metrics = mock(CoapAdapterMetrics.class);
        this.properties = givenDefaultConfigurationProperties();

        createClients();
        prepareClients();

        commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(eq(false), any())).thenReturn(Future.succeededFuture());

        resourceLimitChecks = mock(ResourceLimitChecks.class);
    }

    /**
     * Cleans up fixture.
     */
    @AfterAll
    public static void shutDown() {
        vertx.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CoapAdapterProperties givenDefaultConfigurationProperties() {
        properties = new CoapAdapterProperties();
        properties.setInsecurePortEnabled(true);
        properties.setAuthenticationRequired(false);

        return properties;
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is invoked if the coap server has been started successfully.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartInvokesOnStartupSuccess(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.succeeding(v -> {
            // THEN the onStartupSuccess method has been invoked
            ctx.verify(() -> verify(startupHandler).handle(any()));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter registers resources as part of the start-up process.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartRegistersResources(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        // and a set of resources
        final Resource resource = mock(Resource.class);
        when(resource.getName()).thenReturn("test");
        adapter.addResources(Set.of(resource));

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.succeeding(s -> {
            // THEN the resource has been registered with the server
            ctx.verify(() -> verify(server).add(resource));
            ctx.completeNow();
        }));
        adapter.start(startupTracker);

    }

    /**
     * Verifies that the resources registered with the adapter are always
     * executed on the adapter's vert.x context.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testResourcesAreRunOnVertxContext(final VertxTestContext ctx) {

        // GIVEN an adapter
        final Context context = vertx.getOrCreateContext();
        final var props = new CoapAdapterProperties();
        final CoapEndpointFactory endpointFactory = mock(CoapEndpointFactory.class);
        when(endpointFactory.getCoapServerConfiguration()).thenReturn(Future.succeededFuture(new Configuration()));
        when(endpointFactory.getInsecureEndpoint()).thenReturn(Future.failedFuture("not implemented"));
        when(endpointFactory.getSecureEndpoint()).thenReturn(Future.succeededFuture(mock(Endpoint.class)));
        adapter = new AbstractVertxBasedCoapAdapter<>() {

            @Override
            public String getTypeName() {
                return "test";
            }
        };
        adapter.setConfig(props);
        adapter.setCoapEndpointFactory(endpointFactory);
        adapter.setMetrics(metrics);
        adapter.setResourceLimitChecks(resourceLimitChecks);

        adapter.setTenantClient(tenantClient);
        adapter.setMessagingClientProviders(createMessagingClientProviders());
        adapter.setRegistrationClient(registrationClient);
        adapter.setCredentialsClient(credentialsClient);
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setCommandRouterClient(commandRouterClient);

        // with a resource
        final Promise<Void> resourceInvocation = Promise.promise();
        final Resource resource = new CoapResource("test") {

            @Override
            public void handleGET(final CoapExchange exchange) {
                ctx.verify(() -> assertThat(Vertx.currentContext()).isEqualTo(context));
                resourceInvocation.complete();
            }
        };

        adapter.addResources(Set.of(resource));
        adapter.init(vertx, context);

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future()
            .compose(ok -> {
                // WHEN the resource receives a GET request
                final Request request = new Request(Code.GET);
                final Object identity = "dummy";
                final Exchange getExchange = new Exchange(request, identity, Origin.REMOTE, mock(Executor.class));
                resource.handleRequest(getExchange);
                // THEN the resource's handler runs on the adapter's vert.x event loop
                return resourceInvocation.future();
            })
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if no credentials authentication provider is
     * set.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartUpFailsIfCredentialsClientFactoryIsNotSet(final VertxTestContext ctx) {

        // GIVEN an adapter that has not all required service clients set
        server = getCoapServer(false);
        adapter = getAdapter(server, properties, false, startupHandler);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        // THEN startup has failed
        startupTracker.future().onComplete(ctx.failing(t -> {
            // and the onStartupSuccess method has not been invoked
            ctx.verify(() -> verify(startupHandler, never()).handle(any()));
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if a client provided coap server fails to
     * start.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartDoesNotInvokeOnStartupSuccessIfStartupFails(final VertxTestContext ctx) {

        // GIVEN an adapter with a client provided http server that fails to bind to a socket when started
        server = getCoapServer(true);
        adapter = getAdapter(server, properties, true, startupHandler);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);
        // THEN the onStartupSuccess method has not been invoked, see ctx.fail
        startupTracker.future().onComplete(ctx.failing(s -> {
            ctx.verify(() -> verify(startupHandler, never()).handle(any()));
            ctx.completeNow();
        }));
    }

    private CoapClusterNodeRegistry givenAClusterNodeRegistry() {
        final CoapClusterNodeRegistry registry = mock(CoapClusterNodeRegistry.class);
        when(registry.registerNode(anyInt(), any(InetSocketAddress.class), any(Duration.class)))
                .thenReturn(Future.succeededFuture());
        when(registry.heartbeat(anyInt(), any(Duration.class)))
                .thenReturn(Future.succeededFuture());
        when(registry.unregisterNode(anyInt()))
                .thenReturn(Future.succeededFuture());
        return registry;
    }

    private CacheBasedClusterNodesProvider givenAClusterNodesProvider() {
        final CacheBasedClusterNodesProvider provider = mock(CacheBasedClusterNodesProvider.class);
        when(provider.refresh()).thenReturn(Future.succeededFuture());
        return provider;
    }

    private void givenClusterModeProperties() {
        properties.setClusterEnabled(true);
        properties.setCidNodeId(42);
        properties.setClusterBindAddress("10.0.0.1");
        properties.setClusterPort(5685);
        properties.setClusterHeartbeat(Duration.ofMillis(50));
        properties.setClusterNodeTtl(Duration.ofSeconds(30));
    }

    /**
     * Verifies that when cluster mode is enabled, the adapter registers the node in the cluster
     * registry and periodically renews the registration and refreshes the cluster nodes during startup,
     * and stops renewing and unregisters the node during shutdown.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterLifecycle(final VertxTestContext ctx) {

        givenClusterModeProperties();
        givenAnAdapter(properties);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        final CacheBasedClusterNodesProvider provider = givenAClusterNodesProvider();
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(provider);

        final Promise<Void> startPromise = Promise.promise();
        adapter.start(startPromise);

        startPromise.future()
                .compose(v -> {
                    ctx.verify(() -> {
                        verify(registry).registerNode(eq(42), eq(new InetSocketAddress("10.0.0.1", 5685)), eq(Duration.ofSeconds(30)));
                        verify(provider).refresh();
                        assertThat(adapter.getClusterMembership().isJoined()).isTrue();
                    });

                    final Promise<Void> heartbeatPromise = Promise.promise();
                    vertx.setTimer(120, timerId -> {
                        ctx.verify(() -> {
                            verify(registry, atLeastOnce()).heartbeat(eq(42), eq(Duration.ofSeconds(30)));
                            verify(provider, atLeast(2)).refresh();
                        });
                        heartbeatPromise.complete();
                    });
                    return heartbeatPromise.future();
                })
                .compose(v -> {
                    final Promise<Void> stopPromise = Promise.promise();
                    adapter.stop(stopPromise);
                    return stopPromise.future();
                })
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(registry).unregisterNode(eq(42));
                        verify(server).stop();
                        assertThat(adapter.getClusterMembership().isJoined()).isFalse();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that when a periodic heartbeat fails, the adapter attempts re-registration of the node.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testHeartbeatFailureTriggersReRegistration(final VertxTestContext ctx) {

        givenClusterModeProperties();
        givenAnAdapter(properties);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        when(registry.heartbeat(anyInt(), any(Duration.class)))
                .thenReturn(Future.failedFuture(new IllegalStateException("lease expired")));
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startPromise = Promise.promise();
        adapter.start(startPromise);

        startPromise.future()
                .compose(v -> {
                    verify(registry).registerNode(eq(42), eq(new InetSocketAddress("10.0.0.1", 5685)), eq(Duration.ofSeconds(30)));

                    final Promise<Void> reRegPromise = Promise.promise();
                    vertx.setTimer(120, timerId -> {
                        ctx.verify(() -> {
                            verify(registry, atLeastOnce()).heartbeat(eq(42), eq(Duration.ofSeconds(30)));
                            verify(registry, atLeast(2)).registerNode(
                                    eq(42),
                                    eq(new InetSocketAddress("10.0.0.1", 5685)),
                                    eq(Duration.ofSeconds(30)));
                        });
                        reRegPromise.complete();
                    });
                    return reRegPromise.future();
                })
                .compose(v -> {
                    final Promise<Void> stopPromise = Promise.promise();
                    adapter.stop(stopPromise);
                    return stopPromise.future();
                })
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that startup fails when cluster mode is enabled but no cluster node registry is set.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterStartupFailsWhenRegistryNotSet(final VertxTestContext ctx) {

        properties.setClusterEnabled(true);
        properties.setCidNodeId(1);
        givenAnAdapter(properties);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(IllegalStateException.class);
                assertThat(t.getMessage()).contains("clusterNodeRegistry property must be set");
                verify(server, never()).start();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that startup fails when cluster mode is enabled but no cluster nodes provider is set.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterStartupFailsWhenNodesProviderNotSet(final VertxTestContext ctx) {

        properties.setClusterEnabled(true);
        properties.setCidNodeId(1);
        givenAnAdapter(properties);
        adapter.setClusterNodeRegistry(givenAClusterNodeRegistry());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(IllegalStateException.class);
                assertThat(t.getMessage()).contains("clusterNodesProvider property must be set");
                verify(server, never()).start();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that startup fails when cluster mode is enabled and registry is set but cidNodeId is negative.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterStartupFailsWhenNodeIdNegative(final VertxTestContext ctx) {

        properties.setClusterEnabled(true);
        properties.setCidNodeId(-1);
        givenAnAdapter(properties);
        adapter.setClusterNodeRegistry(givenAClusterNodeRegistry());
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(IllegalStateException.class);
                assertThat(t.getMessage()).contains("cidNodeId must be configured");
                verify(server, never()).start();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the advertised address instead of the bind address is registered.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterRegistersAdvertisedAddress(final VertxTestContext ctx) {

        givenClusterModeProperties();
        properties.setClusterBindAddress("0.0.0.0");
        properties.setClusterAdvertisedAddress("10.0.0.7");
        givenAnAdapter(properties);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future()
                .compose(v -> {
                    ctx.verify(() -> verify(registry).registerNode(
                            eq(42),
                            eq(new InetSocketAddress("10.0.0.7", 5685)),
                            any(Duration.class)));
                    final Promise<Void> stopPromise = Promise.promise();
                    adapter.stop(stopPromise);
                    return stopPromise.future();
                })
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that startup fails if the cluster connector is bound to a wildcard address
     * and no advertised address has been configured.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterStartupFailsForWildcardAddress(final VertxTestContext ctx) {

        givenClusterModeProperties();
        properties.setClusterBindAddress("::");
        givenAnAdapter(properties);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(IllegalStateException.class);
                assertThat(t.getMessage()).contains("advertised-address must be set");
                verify(registry, never()).registerNode(anyInt(), any(InetSocketAddress.class), any(Duration.class));
                verify(server, never()).start();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that startup fails if the heartbeat interval is not shorter than the node TTL.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterStartupFailsForHeartbeatNotShorterThanNodeTtl(final VertxTestContext ctx) {

        givenClusterModeProperties();
        properties.setClusterHeartbeat(Duration.ofSeconds(30));
        properties.setClusterNodeTtl(Duration.ofSeconds(30));
        givenAnAdapter(properties);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(IllegalStateException.class);
                assertThat(t.getMessage()).contains("must be shorter than the cluster node TTL");
                verify(registry, never()).registerNode(anyInt(), any(InetSocketAddress.class), any(Duration.class));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that startup fails in cluster mode if the secure endpoint, which contains the
     * DTLS cluster connector, cannot be created, even if the insecure endpoint can be created.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterStartupFailsWhenSecureEndpointCannotBeCreated(final VertxTestContext ctx) {

        givenClusterModeProperties();
        final CoapEndpointFactory endpointFactory = mock(CoapEndpointFactory.class);
        when(endpointFactory.getCoapServerConfiguration()).thenReturn(Future.succeededFuture(new Configuration()));
        when(endpointFactory.getInsecureEndpoint()).thenReturn(Future.succeededFuture(mock(Endpoint.class)));
        when(endpointFactory.getSecureEndpoint())
                .thenReturn(Future.failedFuture(new IllegalStateException("mac-secret must be set")));
        adapter = new AbstractVertxBasedCoapAdapter<>() {

            @Override
            public String getTypeName() {
                return "test";
            }
        };
        adapter.setConfig(properties);
        adapter.setCoapEndpointFactory(endpointFactory);
        adapter.setMetrics(metrics);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        adapter.setTenantClient(tenantClient);
        adapter.setMessagingClientProviders(createMessagingClientProviders());
        adapter.setRegistrationClient(registrationClient);
        adapter.setCredentialsClient(credentialsClient);
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setCommandRouterClient(commandRouterClient);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());
        adapter.init(vertx, vertx.getOrCreateContext());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).hasMessageThat().contains("mac-secret must be set");
                verify(registry, never()).registerNode(anyInt(), any(InetSocketAddress.class), any(Duration.class));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter leaves the cluster again if the CoAP server cannot be started.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterIsLeftWhenServerStartFails(final VertxTestContext ctx) {

        givenClusterModeProperties();
        this.server = getCoapServer(true);
        this.adapter = getAdapter(server, properties, true, startupHandler);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                verify(registry).registerNode(eq(42), any(InetSocketAddress.class), any(Duration.class));
                verify(registry).unregisterNode(eq(42));
                assertThat(adapter.getClusterMembership().isJoined()).isFalse();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that adapter shutdown succeeds even if unregistering the cluster node fails.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testClusterShutdownSucceedsWhenUnregisterFails(final VertxTestContext ctx) {

        givenClusterModeProperties();
        givenAnAdapter(properties);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        when(registry.unregisterNode(anyInt()))
                .thenReturn(Future.failedFuture(new RuntimeException("intended unregister failure")));
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startPromise = Promise.promise();
        adapter.start(startPromise);

        startPromise.future()
                .compose(v -> {
                    final Promise<Void> stopPromise = Promise.promise();
                    adapter.stop(stopPromise);
                    return stopPromise.future();
                })
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> verify(server).stop());
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the CoAP server is stopped even if the <em>preShutdown</em> method throws an exception.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStopStopsServerWhenPreShutdownFails(final VertxTestContext ctx) {

        givenClusterModeProperties();
        preShutdownAction = () -> {
            throw new IllegalStateException("intended preShutdown failure");
        };
        givenAnAdapter(properties);
        final CoapClusterNodeRegistry registry = givenAClusterNodeRegistry();
        adapter.setClusterNodeRegistry(registry);
        adapter.setClusterNodesProvider(givenAClusterNodesProvider());

        final Promise<Void> startPromise = Promise.promise();
        adapter.start(startPromise);

        startPromise.future()
                .compose(v -> {
                    final Promise<Void> stopPromise = Promise.promise();
                    adapter.stop(stopPromise);
                    return stopPromise.future();
                })
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(registry).unregisterNode(eq(42));
                        verify(server).stop();
                    });
                    ctx.completeNow();
                }));
    }

    private CoapServer getCoapServer(final boolean startupShouldFail) {

        final CoapServer server = mock(CoapServer.class);
        if (startupShouldFail) {
            doThrow(new IllegalStateException("Coap Server start with intended failure!")).when(server).start();
        } else {
            doNothing().when(server).start();
        }
        return server;
    }

    /**
     * Creates a new adapter instance to be tested.
     * <p>
     * This method
     * <ol>
     * <li>creates a new {@code CoapServer} by invoking {@link #getCoapServer(boolean)} with {@code false}</li>
     * <li>assigns the result to property <em>server</em></li>
     * <li>creates a new adapter by invoking {@link #getAdapter(CoapServer, CoapAdapterProperties, boolean, Handler)}
     * with the server, configuration, {@code true} and the startupHandler</li>
     * <li>assigns the result to property <em>adapter</em></li>
     * </ol>
     *
     * @param configuration The configuration properties to use.
     * @return The adapter instance.
     */
    private AbstractVertxBasedCoapAdapter<CoapAdapterProperties> givenAnAdapter(final CoapAdapterProperties configuration) {

        this.server = getCoapServer(false);
        this.adapter = getAdapter(server, configuration, true, startupHandler);
        return adapter;
    }


    /**
     * Creates a protocol adapter for a given HTTP server.
     *
     * @param server The coap server.
     * @param configuration The configuration properties to use.
     * @param complete {@code true}, if that adapter should be created with all Hono service clients set, {@code false}, if the
     *            adapter should be created, and all Hono service clients set, but the credentials client is not set.
     * @param onStartupSuccess The handler to invoke on successful startup.
     *
     * @return The adapter.
     */
    private AbstractVertxBasedCoapAdapter<CoapAdapterProperties> getAdapter(
            final CoapServer server,
            final CoapAdapterProperties configuration,
            final boolean complete,
            final Handler<Void> onStartupSuccess) {

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = new AbstractVertxBasedCoapAdapter<>() {

            @Override
            public String getTypeName() {
                return Constants.PROTOCOL_ADAPTER_TYPE_COAP;
            }

            @Override
            protected void onStartupSuccess() {
                Optional.ofNullable(onStartupSuccess).ifPresent(h -> h.handle(null));
            }

            @Override
            protected void preShutdown() {
                Optional.ofNullable(preShutdownAction).ifPresent(Runnable::run);
            }
        };

        adapter.setConfig(configuration);
        adapter.setCoapServer(server);
        adapter.setMetrics(metrics);
        adapter.setResourceLimitChecks(resourceLimitChecks);

        adapter.setTenantClient(tenantClient);
        adapter.setMessagingClientProviders(createMessagingClientProviders());
        adapter.setRegistrationClient(registrationClient);
        if (complete) {
            adapter.setCredentialsClient(credentialsClient);
        }
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setCommandRouterClient(commandRouterClient);
        adapter.init(vertx, mock(Context.class));

        return adapter;
    }
}

