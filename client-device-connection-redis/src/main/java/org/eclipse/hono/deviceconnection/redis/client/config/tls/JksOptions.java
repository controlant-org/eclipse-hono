/*
TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.config.tls;

import java.util.Optional;

import io.quarkus.vertx.core.runtime.config.JksConfiguration;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:JavadocMethod")
public interface JksOptions {
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
    default JksConfiguration convertToJksConfiguration() {
        final JksConfiguration jks = new JksConfiguration();
        jks.enabled = enabled();
        jks.path = path();
        jks.password = password();
        return jks;
    }
}
