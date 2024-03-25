/**
 * TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.config;

import java.util.Optional;

import io.smallrye.config.WithDefault;
import io.vertx.core.net.ProxyType;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:JavadocMethod")
public interface ProxyConfig {

    /**
     * Set proxy username.
     */
    Optional<String> username();

    /**
     * Set proxy password.
     */
    Optional<String> password();

    /**
     * Set proxy port. Defaults to 3128.
     */
    @WithDefault("3128")
    int port();

    /**
     * Set proxy host.
     */
    Optional<String> host();

    /**
     * Set proxy type.
     * Accepted values are: {@code HTTP} (default), {@code SOCKS4} and {@code SOCKS5}.
     */
    @WithDefault("http")
    ProxyType type();

}
