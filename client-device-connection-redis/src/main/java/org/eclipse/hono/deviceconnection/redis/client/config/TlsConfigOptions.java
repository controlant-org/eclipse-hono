/*
TODO.
 */
package org.eclipse.hono.deviceconnection.redis.client.config;

import org.eclipse.hono.deviceconnection.redis.client.config.tls.JksOptions;
import org.eclipse.hono.deviceconnection.redis.client.config.tls.PemKeyCertOptions;
import org.eclipse.hono.deviceconnection.redis.client.config.tls.PemTrustCertOptions;
import org.eclipse.hono.deviceconnection.redis.client.config.tls.PfxOptions;

import io.smallrye.config.WithDefault;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:JavadocMethod")
public interface TlsConfig {

    /**
     * Whether SSL/TLS is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Enable trusting all certificates. Disabled by default.
     */
    @WithDefault("false")
    boolean trustAll();

    /**
     * Trust configuration in the PEM format.
     * <p>
     * When enabled, {@code #trust-certificate-jks} and {@code #trust-certificate-pfx} must be disabled.
     */
    PemTrustCertOptions trustCertificatePem();

    /**
     * Trust configuration in the JKS format.
     * <p>
     * When enabled, {@code #trust-certificate-pem} and {@code #trust-certificate-pfx} must be disabled.
     */
    JksOptions trustCertificateJks();

    /**
     * Trust configuration in the PFX format.
     * <p>
     * When enabled, {@code #trust-certificate-jks} and {@code #trust-certificate-pem} must be disabled.
     */
    PfxOptions trustCertificatePfx();

    /**
     * Key/cert configuration in the PEM format.
     * <p>
     * When enabled, {@code key-certificate-jks} and {@code #key-certificate-pfx} must be disabled.
     */
    PemKeyCertOptions keyCertificatePem();

    /**
     * Key/cert configuration in the JKS format.
     * <p>
     * When enabled, {@code #key-certificate-pem} and {@code #key-certificate-pfx} must be disabled.
     */
    JksOptions keyCertificateJks();

    /**
     * Key/cert configuration in the PFX format.
     * <p>
     * When enabled, {@code key-certificate-jks} and {@code #key-certificate-pem} must be disabled.
     */
    PfxOptions keyCertificatePfx();

    /**
     * The hostname verification algorithm to use in case the server's identity should be checked.
     * Should be {@code HTTPS}, {@code LDAPS} or an {@code NONE} (default).
     * <p>
     * If set to {@code NONE}, it does not verify the hostname.
     * <p>
     */
    @WithDefault("NONE")
    String hostnameVerificationAlgorithm();

}
