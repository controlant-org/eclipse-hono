/*
TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.config.tls;

import java.util.Optional;

import io.quarkus.vertx.core.runtime.config.PfxConfiguration;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:JavadocMethod")
public interface PfxOptions {
    /**
     * TODO.
     */
    @WithParentName
    @WithDefault("false")
    boolean enabled();

    /**
     * TODO.
     */
    Optional<String> path();

    /**
     * TODO.
     */
    Optional<String> password();

    /**
     * TODO.
     */
    default PfxConfiguration convertToPfxConfiguration() {
        final PfxConfiguration jks = new PfxConfiguration();
        jks.enabled = enabled();
        jks.path = path();
        jks.password = password();
        return jks;
    }
}
