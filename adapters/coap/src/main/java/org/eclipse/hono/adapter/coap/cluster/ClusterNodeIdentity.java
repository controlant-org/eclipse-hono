/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap.cluster;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper methods for identifying the CoAP adapter instance that runs in the local process.
 */
public final class ClusterNodeIdentity {

    /**
     * The name of the environment variable that contains the local host name.
     * <p>
     * Kubernetes sets this variable to the name of the pod that a container runs in.
     */
    public static final String ENV_HOSTNAME = "HOSTNAME";

    /**
     * The pattern of the names of pods that belong to a Kubernetes StatefulSet.
     * <p>
     * The name of such a pod consists of the name of the StatefulSet, a hyphen and the pod's ordinal index.
     */
    private static final Pattern STATEFUL_SET_POD_NAME = Pattern.compile("^.+-(\\d{1,9})$");

    private ClusterNodeIdentity() {
        // prevent instantiation
    }

    /**
     * Gets the name of the local host.
     * <p>
     * The name is read from the {@value #ENV_HOSTNAME} environment variable. The name is not
     * determined by means of a DNS lookup, because such a lookup may block for a long time.
     *
     * @return The host name or an empty optional if the environment variable is not set.
     */
    public static Optional<String> localHostName() {
        return Optional.ofNullable(System.getenv(ENV_HOSTNAME))
                .map(String::trim)
                .filter(name -> !name.isEmpty());
    }

    /**
     * Gets the ordinal index of a Kubernetes StatefulSet pod from the pod's name.
     *
     * @param hostName The host name, which is the pod's name in a Kubernetes pod.
     * @return The ordinal index or an empty optional if the name does not end with a hyphen and a number.
     * @throws NullPointerException if host name is {@code null}.
     */
    public static OptionalInt statefulSetOrdinal(final String hostName) {
        Objects.requireNonNull(hostName);
        final Matcher matcher = STATEFUL_SET_POD_NAME.matcher(hostName);
        if (matcher.matches()) {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        }
        return OptionalInt.empty();
    }
}
