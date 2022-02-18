/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.base.jdbc.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;

/**
 * JDBC properties for the device store.
 */
@ConfigMapping(prefix = "hono.registry.jdbc", namingStrategy = NamingStrategy.VERBATIM)
public interface JdbcDeviceStoreOptions {

    /**
     * @return The options.
     */
    JdbcOptions adapter();

    /**
     * @return The options.
     */
    JdbcOptions management();
}
