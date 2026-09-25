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

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.coap.cluster.CacheBasedClusterNodesProvider;
import org.eclipse.hono.adapter.coap.cluster.CoapClusterMembership;
import org.eclipse.hono.adapter.coap.cluster.CoapClusterNodeRegistry;
import org.eclipse.hono.util.Constants;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * Base class for a vert.x based Hono protocol adapter that uses CoAP.
 * <p>
 * Provides support for exposing CoAP resources using plain UDP and DTLS.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractVertxBasedCoapAdapter<T extends CoapAdapterProperties>
        extends AbstractProtocolAdapterBase<T> implements CoapProtocolAdapter {

    /**
     * The CoAP endpoint handling exchanges via DTLS.
     */
    protected Endpoint secureEndpoint;
    /**
     * The CoAP endpoint handling exchanges via UDP.
     */
    protected Endpoint insecureEndpoint;

    private final Set<Resource> resourcesToAdd = new HashSet<>();

    private CoapEndpointFactory endpointFactory;
    private CoapServer server;
    private CoapAdapterMetrics metrics = CoapAdapterMetrics.NOOP;
    private CoapClusterNodeRegistry clusterNodeRegistry;
    private CacheBasedClusterNodesProvider clusterNodesProvider;
    private CoapClusterMembership clusterMembership;

    /**
     * Sets the factory for creating CoAP endpoints.
     * <p>
     * The factory will be used during startup to create endpoints for the CoAP server if no
     * server has been set explicitly using {@link #setCoapServer(CoapServer)}.
     *
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    public final void setCoapEndpointFactory(final CoapEndpointFactory factory) {
        this.endpointFactory = Objects.requireNonNull(factory);
    }

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    public final void setMetrics(final CoapAdapterMetrics metrics) {
        Optional.ofNullable(metrics)
            .ifPresent(m -> log.info("reporting metrics using [{}]", metrics.getClass().getName()));
        this.metrics = metrics;
    }

    @Override
    public final CoapAdapterMetrics getMetrics() {
        return metrics;
    }

    /**
     * Sets the registry for tracking active CoAP cluster nodes.
     *
     * @param clusterNodeRegistry The registry.
     */
    public void setClusterNodeRegistry(final CoapClusterNodeRegistry clusterNodeRegistry) {
        this.clusterNodeRegistry = clusterNodeRegistry;
    }

    /**
     * Sets the provider for Californium DTLS cluster node discovery.
     *
     * @param clusterNodesProvider The provider.
     */
    public void setClusterNodesProvider(final CacheBasedClusterNodesProvider clusterNodesProvider) {
        this.clusterNodesProvider = clusterNodesProvider;
    }

    CoapClusterMembership getClusterMembership() {
        return clusterMembership;
    }

    /**
     * Gets the socket address used for cluster internal communication.
     *
     * @return The socket address.
     */
    protected InetSocketAddress getClusterAddress() {
        return new InetSocketAddress(getConfig().getClusterBindAddress(), getConfig().getClusterPort());
    }

    /**
     * Adds CoAP resources that should be added to the CoAP server
     * managed by this class.
     *
     * @param resources The resources.
     * @throws NullPointerException if resources is {@code null}.
     */
    public final void addResources(final Set<Resource> resources) {
        this.resourcesToAdd.addAll(Objects.requireNonNull(resources));
    }

    /**
     * @return {@link CoAP#DEFAULT_COAP_SECURE_PORT}
     */
    @Override
    public final int getPortDefaultValue() {
        return CoAP.DEFAULT_COAP_SECURE_PORT;
    }

    /**
     * @return {@link CoAP#DEFAULT_COAP_PORT}
     */
    @Override
    public final int getInsecurePortDefaultValue() {
        return CoAP.DEFAULT_COAP_PORT;
    }

    @Override
    protected final int getActualPort() {
        return Optional.ofNullable(secureEndpoint)
                .map(ep -> ep.getAddress().getPort())
                .orElse(Constants.PORT_UNCONFIGURED);
    }

    @Override
    protected final int getActualInsecurePort() {
        return Optional.ofNullable(insecureEndpoint)
                .map(ep -> ep.getAddress().getPort())
                .orElse(Constants.PORT_UNCONFIGURED);
    }

    /**
     * Sets the coap server instance configured to serve requests.
     * <p>
     * If no server is set using this method, then a server instance is created during startup of this adapter based on
     * the <em>config</em> properties.
     *
     * @param server The coap server.
     * @throws NullPointerException if server is {@code null}.
     */
    public final void setCoapServer(final CoapServer server) {
        Objects.requireNonNull(server);
        this.server = server;
    }

    @Override
    public final Endpoint getSecureEndpoint() {
        return secureEndpoint;
    }

    @Override
    public final Endpoint getInsecureEndpoint() {
        return insecureEndpoint;
    }

    @Override
    public final void runOnContext(final Handler<Void> codeToRun) {
        context.runOnContext(codeToRun);
    }

    @Override
    public final void doStart(final Promise<Void> startPromise) {

        Optional.ofNullable(server)
                .map(Future::succeededFuture)
                .orElseGet(this::createServer)
                .compose(serverToStart -> preStartup().map(serverToStart))
                .compose(serverToStart -> joinCluster().map(serverToStart))
                .onSuccess(this::addResources)
                .compose(serverToStart -> vertx.executeBlocking(() -> {
                    serverToStart.start();
                    return serverToStart;
                }))
                .compose(serverToStart -> {
                    try {
                        onStartupSuccess();
                        return Future.succeededFuture((Void) null);
                    } catch (final Exception t) {
                        log.error("error executing onStartupSuccess", t);
                        return Future.failedFuture(t);
                    }
                })
                .recover(t -> leaveCluster().transform(v -> Future.<Void>failedFuture(t)))
                .onComplete(startPromise);
    }

    private Future<CoapServer> createServer() {

        if (endpointFactory == null) {
            return Future.failedFuture(new IllegalStateException(
                    "coapEndpointFactory property must be set if no CoAP server is set explicitly"));
        }

        log.info("creating new CoAP server");
        return endpointFactory.getCoapServerConfiguration()
                .onFailure(t -> log.info("not creating coap server: {}", t.getMessage()))
                .compose(config -> {
                    final CoapServer newServer = new CoapServer(config) {
                        @Override
                        protected Resource createRoot() {
                            return new HonoRootResource(AbstractVertxBasedCoapAdapter.this::getContext);
                        }
                    };
                    final InternalErrorTracer internalErrorTracer = new InternalErrorTracer(getTypeName(), tracer);
                    final Future<Endpoint> secureEndpointFuture = endpointFactory.getSecureEndpoint()
                            .onFailure(t -> log.info("not creating secure endpoint: {}", t.getMessage()))
                            .onSuccess(ep -> {
                                ep.addInterceptor(internalErrorTracer);
                                newServer.addEndpoint(ep);
                                this.secureEndpoint = ep;
                            });
                    final Future<Endpoint> insecureEndpointFuture = endpointFactory.getInsecureEndpoint()
                            .onFailure(t -> log.info("not creating insecure endpoint: {}", t.getMessage()))
                            .onSuccess(ep -> {
                                ep.addInterceptor(internalErrorTracer);
                                newServer.addEndpoint(ep);
                                this.insecureEndpoint = ep;
                            });

                    return Future.any(insecureEndpointFuture, secureEndpointFuture)
                            .map(ok -> {
                                this.server = newServer;
                                return newServer;
                            });
                });
    }

    private void addResources(final CoapServer startingServer) {
        resourcesToAdd.forEach(resource -> {
            log.info("adding resource to CoAP server [name: {}]", resource.getName());
            startingServer.add(resource);
        });
        resourcesToAdd.clear();
    }

    /**
     * Invoked before the CoAP server is started.
     * <p>
     * May be overridden by sub-classes to provide additional startup handling.
     *
     * @return A future indicating the outcome of the operation. The start up process fails if the returned future
     *         fails.
     */
    protected Future<Void> preStartup() {

        return Future.succeededFuture();
    }

    /**
     * Joins the cluster of adapter instances if cluster mode is enabled.
     * <p>
     * The cluster is joined before the CoAP server is started. This is safe because other cluster nodes
     * only forward DTLS records that contain a connection ID issued by this node, and no such connection
     * IDs exist before the server has been started.
     *
     * @return A future indicating the outcome of the operation.
     */
    private Future<Void> joinCluster() {

        final T config = getConfig();
        if (!config.isClusterEnabled()) {
            return Future.succeededFuture();
        }
        if ("0.0.0.0".equals(config.getClusterBindAddress())) {
            log.warn("Cluster mode is enabled but clusterBindAddress is '0.0.0.0'."
                    + " In multi-node deployments, clusterBindAddress should be set to the reachable pod IP or hostname");
        }
        if (clusterNodeRegistry == null) {
            return Future.failedFuture(
                    new IllegalStateException("clusterNodeRegistry property must be set when cluster mode is enabled"));
        }
        if (clusterNodesProvider == null) {
            return Future.failedFuture(
                    new IllegalStateException("clusterNodesProvider property must be set when cluster mode is enabled"));
        }
        if (config.getCidNodeId() < 0) {
            return Future.failedFuture(
                    new IllegalStateException("cidNodeId must be configured when cluster mode is enabled"));
        }
        clusterMembership = new CoapClusterMembership(
                vertx,
                clusterNodeRegistry,
                clusterNodesProvider,
                config.getCidNodeId(),
                getClusterAddress(),
                config.getClusterNodeTtl(),
                config.getClusterHeartbeat(),
                CoapClusterMembership.DEFAULT_LEAVE_TIMEOUT);
        return clusterMembership.join();
    }

    private Future<Void> leaveCluster() {
        return Optional.ofNullable(clusterMembership)
                .map(CoapClusterMembership::leave)
                .orElseGet(Future::succeededFuture);
    }

    /**
     * Invoked after this adapter has started up successfully.
     * <p>
     * May be overridden by sub-classes.
     */
    protected void onStartupSuccess() {
        // empty
    }

    @Override
    public final void doStop(final Promise<Void> stopPromise) {

        try {
            preShutdown();
        } catch (final Exception e) {
            log.error("error in preShutdown", e);
        }

        // leaving the cluster never fails and is limited in time
        leaveCluster()
                .compose(v -> vertx.executeBlocking(() -> {
                    if (server != null) {
                        server.stop();
                    }
                    return (Void) null;
                }))
                .compose(ok -> postShutdown())
                .onComplete(stopPromise);
    }

    /**
     * Invoked before the CoAP server is shut down. May be overridden by sub-classes.
     */
    protected void preShutdown() {
        // empty
    }

    /**
     * Invoked after the Adapter has been shutdown successfully. May be overridden by sub-classes to provide further
     * shutdown handling.
     *
     * @return A future that has to be completed when this operation is finished.
     */
    protected Future<Void> postShutdown() {
        return Future.succeededFuture();
    }
}

