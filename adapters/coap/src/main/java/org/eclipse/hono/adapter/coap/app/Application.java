/**
 * Copyright (c) 2021, 2026 Contributors to the Eclipse Foundation
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

import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.AbstractProtocolAdapterApplication;
import org.eclipse.hono.adapter.coap.CoapAdapterMetrics;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CommandResponseResource;
import org.eclipse.hono.adapter.coap.DeviceRegistryBasedCertificateVerifier;
import org.eclipse.hono.adapter.coap.DeviceRegistryBasedPskStore;
import org.eclipse.hono.adapter.coap.EventResource;
import org.eclipse.hono.adapter.coap.TelemetryResource;
import org.eclipse.hono.adapter.coap.cluster.CacheBasedClusterNodesProvider;
import org.eclipse.hono.adapter.coap.cluster.CacheBasedCoapClusterNodeRegistry;
import org.eclipse.hono.adapter.coap.cluster.ClusterNodeIdentity;
import org.eclipse.hono.adapter.coap.impl.ConfigBasedCoapEndpointFactory;
import org.eclipse.hono.adapter.coap.impl.VertxBasedCoapAdapter;
import org.eclipse.hono.deviceconnection.common.Cache;
import org.eclipse.hono.service.ApplicationConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * The Hono CoAP adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication<CoapAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    CoapAdapterMetrics metrics;

    @Inject
    Instance<Cache<String, String>> cacheInstance;

    /**
     * {@inheritDoc}
     * <p>
     * In cluster mode, verifies that the cache required for sharing cluster state has been
     * configured, determines the node ID to use in connection IDs and limits the number of
     * adapter verticle instances to one before deploying the adapter.
     */
    @Override
    protected void doStart() {
        if (protocolAdapterProperties.isClusterEnabled()) {
            CacheProducer.checkRedisHostsConfigured(ConfigProvider.getConfig());
            protocolAdapterProperties.setCidNodeId(resolveCidNodeId(
                    protocolAdapterProperties.getCidNodeId(),
                    ClusterNodeIdentity.localHostName()));
            limitToSingleAdapterInstance(appConfig);
        }
        super.doStart();
    }

    /**
     * Determines the cluster node ID to use in connection IDs.
     * <p>
     * An explicitly configured node ID is used as is. Otherwise, the node ID is the ordinal index of the
     * Kubernetes StatefulSet pod that the adapter runs in, which is determined from the host name.
     *
     * @param configuredNodeId The configured node ID or a negative number if no node ID has been configured.
     * @param hostName The local host name.
     * @return The node ID.
     * @throws IllegalStateException if no node ID has been configured and the host name does not contain
     *                               an ordinal index that can be used as a node ID.
     */
    static int resolveCidNodeId(final int configuredNodeId, final Optional<String> hostName) {
        if (configuredNodeId >= 0) {
            return configuredNodeId;
        }
        final String name = hostName.orElseThrow(() -> new IllegalStateException(
                "cluster mode requires a node ID, hono.coap.dtls.cid.node-id must be set"
                + " if the adapter does not run in a Kubernetes StatefulSet"));
        final int ordinal = ClusterNodeIdentity.statefulSetOrdinal(name).orElseThrow(() -> new IllegalStateException(
                "cluster mode requires a node ID, hono.coap.dtls.cid.node-id must be set because host name ["
                + name + "] does not end with the ordinal index of a Kubernetes StatefulSet pod"));
        if (ordinal > CacheBasedCoapClusterNodeRegistry.MAX_NODE_ID) {
            throw new IllegalStateException(String.format(
                    "StatefulSet pod ordinal %d of host [%s] exceeds the maximum cluster node ID %d",
                    ordinal, name, CacheBasedCoapClusterNodeRegistry.MAX_NODE_ID));
        }
        LOG.info("using StatefulSet pod ordinal of host [{}] as cluster node ID [{}]", name, ordinal);
        return ordinal;
    }

    /**
     * Limits the number of adapter verticle instances to deploy to one.
     * <p>
     * Each adapter verticle instance creates its own CoAP server whose DTLS connector binds to the
     * configured secure port and, in cluster mode, to the cluster port. A UDP port can only be bound by
     * a single socket, and the DTLS connection state of a device must be kept by a single connector.
     * Only one adapter verticle instance per process can therefore take part in a cluster.
     *
     * @param appConfig The application configuration to adjust.
     */
    static void limitToSingleAdapterInstance(final ApplicationConfigProperties appConfig) {
        final int configuredInstances = appConfig.getMaxInstances();
        if (configuredInstances > 1) {
            LOG.warn("cluster mode supports a single adapter verticle instance only, deploying 1 instead of {} instances",
                    configuredInstances);
        }
        appConfig.setMaxInstances(1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected VertxBasedCoapAdapter adapter() {

        final var adapter = new VertxBasedCoapAdapter();
        adapter.setConfig(protocolAdapterProperties);
        adapter.setMetrics(metrics);
        setCollaborators(adapter);
        adapter.addResources(Set.of(
                new TelemetryResource(TelemetryConstants.TELEMETRY_ENDPOINT, adapter, tracer, vertx),
                new TelemetryResource(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT, adapter, tracer, vertx),
                new EventResource(EventConstants.EVENT_ENDPOINT, adapter, tracer, vertx),
                new EventResource(EventConstants.EVENT_ENDPOINT_SHORT, adapter, tracer, vertx),
                new CommandResponseResource(CommandConstants.COMMAND_RESPONSE_ENDPOINT, adapter, tracer, vertx),
                new CommandResponseResource(CommandConstants.COMMAND_RESPONSE_ENDPOINT_SHORT, adapter, tracer, vertx)));

        final var endpointFactory = new ConfigBasedCoapEndpointFactory(vertx, protocolAdapterProperties);
        endpointFactory.setMetrics(metrics);
        endpointFactory.setPskStore(new DeviceRegistryBasedPskStore(adapter, tracer));
        endpointFactory.setCertificateVerifier(new DeviceRegistryBasedCertificateVerifier(vertx, adapter, tracer));

        if (protocolAdapterProperties.isClusterEnabled()) {
            if (cacheInstance == null || !cacheInstance.isResolvable()) {
                throw new IllegalStateException("cluster mode is enabled but no cache is available");
            }
            final Cache<String, String> cache = cacheInstance.get();
            final CacheBasedCoapClusterNodeRegistry registry = new CacheBasedCoapClusterNodeRegistry(cache);
            adapter.setClusterNodeRegistry(registry);
            final CacheBasedClusterNodesProvider provider = new CacheBasedClusterNodesProvider(registry);
            adapter.setClusterNodesProvider(provider);
            endpointFactory.setClusterNodesProvider(provider);
        }

        adapter.setCoapEndpointFactory(endpointFactory);

        return adapter;
    }
}

