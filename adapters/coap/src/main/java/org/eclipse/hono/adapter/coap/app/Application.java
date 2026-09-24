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
import org.eclipse.hono.adapter.coap.impl.ConfigBasedCoapEndpointFactory;
import org.eclipse.hono.adapter.coap.impl.VertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.session.CacheBasedDtlsSessionStore;
import org.eclipse.hono.deviceconnection.common.Cache;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
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
        endpointFactory.setPskStore(new DeviceRegistryBasedPskStore(adapter, tracer));
        endpointFactory.setCertificateVerifier(new DeviceRegistryBasedCertificateVerifier(vertx, adapter, tracer));

        if (protocolAdapterProperties.isClusterEnabled() || protocolAdapterProperties.isSessionResumptionEnabled()) {
            if (cacheInstance != null && cacheInstance.isResolvable()) {
                final Cache<String, String> cache = cacheInstance.get();
                final CacheBasedCoapClusterNodeRegistry registry = new CacheBasedCoapClusterNodeRegistry(cache);
                adapter.setClusterNodeRegistry(registry);
                final CacheBasedClusterNodesProvider provider = new CacheBasedClusterNodesProvider(registry);
                adapter.setClusterNodesProvider(provider);
                endpointFactory.setClusterNodesProvider(provider);
                final CacheBasedDtlsSessionStore sessionStore = new CacheBasedDtlsSessionStore(cache, metrics);
                endpointFactory.setSessionStore(sessionStore);
            } else if (protocolAdapterProperties.isClusterEnabled()) {
                LOG.warn("Cluster mode is enabled, but no Cache bean is available");
            } else {
                LOG.info("Session resumption is enabled, but no Cache bean is available. Resumption with central store is disabled");
            }
        }

        adapter.setCoapEndpointFactory(endpointFactory);

        return adapter;
    }
}
