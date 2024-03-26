/*

 */
package org.eclipse.hono.deviceconnection.redis.client.config;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.core.runtime.SSLConfigHelper;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;

/**
 * TODO.
 */
public class RedisConfigProperties extends RedisOptions {

    public static final String CLIENT_NAME = "hono-deviceconnection";

    /**
     * TODO.
     * @param options TODO.
     * @throws ConfigurationException TODO.
     */
    public RedisConfigProperties(final RedisConfigOptions options) throws ConfigurationException {
        final List<URI> hosts;
        if (options.hosts().isPresent()) {
            hosts = new ArrayList<>(options.hosts().get());
            for (URI uri : options.hosts().get()) {
                addConnectionString(uri.toString().trim());
            }
        } else {
            throw new ConfigurationException("Redis host not configured");
        }

        if (RedisClientType.STANDALONE == options.clientType()) {
            if (hosts.size() > 1) {
                throw new ConfigurationException("Multiple Redis hosts supplied for non-clustered configuration");
            }
        }

        options.masterName().ifPresent(this::setMasterName);
        setMaxNestedArrays(options.maxNestedArrays());
        setMaxPoolSize(options.maxPoolSize());
        setMaxPoolWaiting(options.maxPoolWaiting());
        setMaxWaitingHandlers(options.maxWaitingHandlers());

        setProtocolNegotiation(options.protocolNegotiation());

        // TODO: this option isn't available in the currently used version of vertx-redis. Update?
        //config.preferredProtocolVersion().ifPresent(options::setPreferredProtocolVersion);

        // TODO: which way is better?
        setPassword(options.password().orElse(null));
        //options.password().ifPresent(this::setPassword);

        options.poolCleanerInterval().ifPresent(d -> this.setPoolCleanerInterval((int) d.toMillis()));

        setPoolRecycleTimeout((int) options.poolRecycleTimeout().toMillis());
        setHashSlotCacheTTL(options.hashSlotCacheTtl().toMillis());

        options.role().ifPresent(this::setRole);
        setType(options.clientType());
        options.replicas().ifPresent(this::setUseReplicas);

        setNetClientOptions(toNetClientOptions(options));

        setPoolName(CLIENT_NAME);
        // Use the convention defined by Quarkus Micrometer Vert.x metrics to create metrics prefixed with redis.
        // and the client_name as tag.
        // See io.quarkus.micrometer.runtime.binder.vertx.VertxMeterBinderAdapter.extractPrefix and
        // io.quarkus.micrometer.runtime.binder.vertx.VertxMeterBinderAdapter.extractClientName
        getNetClientOptions().setMetricsName("redis|" + CLIENT_NAME);
    }

    /**
     * TODO.
     * @param config TODO.
     * @return TODO.
     */
    private static NetClientOptions toNetClientOptions(final RedisConfigOptions config) {
        final NetConfigOptions tcp = config.tcp();
        final TlsConfigOptions tls = config.tls();
        final NetClientOptions net = new NetClientOptions();

        tcp.alpn().ifPresent(net::setUseAlpn);
        tcp.applicationLayerProtocols().ifPresent(net::setApplicationLayerProtocols);
        tcp.connectionTimeout().ifPresent(d -> net.setConnectTimeout((int) d.toMillis()));

        final String verificationAlgorithm = tls.hostnameVerificationAlgorithm();
        if ("NONE".equalsIgnoreCase(verificationAlgorithm)) {
            net.setHostnameVerificationAlgorithm("");
        } else {
            net.setHostnameVerificationAlgorithm(verificationAlgorithm);
        }

        tcp.idleTimeout().ifPresent(d -> net.setIdleTimeout((int) d.toSeconds()));

        tcp.keepAlive().ifPresent(b -> net.setTcpKeepAlive(true));
        tcp.noDelay().ifPresent(b -> net.setTcpNoDelay(true));

        net.setSsl(tls.enabled()).setTrustAll(tls.trustAll());

        SSLConfigHelper.configurePemTrustOptions(net, tls.trustCertificatePem().convertToPemTrustCertConfiguration());
        SSLConfigHelper.configureJksTrustOptions(net, tls.trustCertificateJks().convertToJksConfiguration());
        SSLConfigHelper.configurePfxTrustOptions(net, tls.trustCertificatePfx().convertToPfxConfiguration());

        SSLConfigHelper.configurePemKeyCertOptions(net, tls.keyCertificatePem().convertToPemKeyCertConfiguration());
        SSLConfigHelper.configureJksKeyCertOptions(net, tls.keyCertificateJks().convertToJksConfiguration());
        SSLConfigHelper.configurePfxKeyCertOptions(net, tls.keyCertificatePfx().convertToPfxConfiguration());

        net.setReconnectAttempts(config.reconnectAttempts());
        net.setReconnectInterval(config.reconnectInterval().toMillis());

        tcp.localAddress().ifPresent(net::setLocalAddress);
        tcp.nonProxyHosts().ifPresent(net::setNonProxyHosts);
        if (tcp.proxyOptions().host().isPresent()) {
            final ProxyOptions po = new ProxyOptions();
            po.setHost(tcp.proxyOptions().host().get());
            po.setType(tcp.proxyOptions().type());
            po.setPort(tcp.proxyOptions().port());
            tcp.proxyOptions().username().ifPresent(po::setUsername);
            tcp.proxyOptions().password().ifPresent(po::setPassword);
            net.setProxyOptions(po);
        }
        tcp.readIdleTimeout().ifPresent(d -> net.setReadIdleTimeout((int) d.toSeconds()));
        tcp.reconnectAttempts().ifPresent(net::setReconnectAttempts);
        tcp.reconnectInterval().ifPresent(v -> net.setReconnectInterval(v.toMillis()));
        tcp.reuseAddress().ifPresent(net::setReuseAddress);
        tcp.reusePort().ifPresent(net::setReusePort);
        tcp.receiveBufferSize().ifPresent(net::setReceiveBufferSize);
        tcp.sendBufferSize().ifPresent(net::setSendBufferSize);
        tcp.soLinger().ifPresent(d -> net.setSoLinger((int) d.toMillis()));
        tcp.secureTransportProtocols().ifPresent(net::setEnabledSecureTransportProtocols);
        tcp.trafficClass().ifPresent(net::setTrafficClass);
        tcp.noDelay().ifPresent(net::setTcpNoDelay);
        tcp.cork().ifPresent(net::setTcpCork);
        tcp.keepAlive().ifPresent(net::setTcpKeepAlive);
        tcp.fastOpen().ifPresent(net::setTcpFastOpen);
        tcp.quickAck().ifPresent(net::setTcpQuickAck);
        tcp.writeIdleTimeout().ifPresent(d -> net.setWriteIdleTimeout((int) d.toSeconds()));

        return net;
    }
}
