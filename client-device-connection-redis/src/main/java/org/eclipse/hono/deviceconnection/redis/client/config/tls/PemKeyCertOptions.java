/*
TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.config.tls;

import java.util.List;
import java.util.Optional;

import io.quarkus.vertx.core.runtime.config.PemKeyCertConfiguration;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:JavadocMethod")
public interface PemKeyCertOptions {
    /**
     * TODO.
     */
    @WithParentName
    @WithDefault("false")
    boolean enabled();

    /**
     * TODO.
     */
    Optional<List<String>> keys();

    /**
     * TODO.
     */
    Optional<List<String>> certs();

    /**
     * TODO.
     */
    default PemKeyCertConfiguration convertToPemKeyCertConfiguration() {
        final PemKeyCertConfiguration pemKeyCert = new PemKeyCertConfiguration();
        pemKeyCert.enabled = enabled();
        pemKeyCert.keys = keys();
        pemKeyCert.certs = certs();
        return pemKeyCert;
    }
}
