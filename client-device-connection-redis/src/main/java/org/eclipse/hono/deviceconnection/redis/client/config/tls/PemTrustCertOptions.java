/*
TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.config.tls;

import java.util.List;
import java.util.Optional;

import io.quarkus.vertx.core.runtime.config.PemTrustCertConfiguration;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:JavadocMethod")
public interface PemTrustCertOptions {
    /**
     * TODO.
     */
    @WithParentName
    @WithDefault("false")
    boolean enabled();

    /**
     * TODO.
     */
    Optional<List<String>> certs();

    /**
     * TODO.
     */
    default PemTrustCertConfiguration convertToPemTrustCertConfiguration() {
        final PemTrustCertConfiguration trustCertificatePem = new PemTrustCertConfiguration();
        trustCertificatePem.enabled = enabled();
        trustCertificatePem.certs = certs();
        return trustCertificatePem;
    }
}
