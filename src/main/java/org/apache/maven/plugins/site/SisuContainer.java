/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.site;

import org.apache.maven.api.plugin.MojoException;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

/**
 * A private Sisu container over the plugin class loader.
 * <p>
 * Doxia, the Doxia site tools, Wagon and the Maven 3 settings decrypter are Sisu components. Maven 4 DI
 * ({@code org.apache.maven.api.di}) does not see them and {@code Lookup} does not find components of the plugin
 * class realm, so the mojos cannot inject them. The container is created on first use and is disposed when the mojo
 * finishes.
 */
final class SisuContainer implements AutoCloseable {

    private final PlexusContainer container;

    SisuContainer(ClassLoader classLoader) {
        try {
            ClassWorld world = new ClassWorld("maven-site-plugin", classLoader);
            ContainerConfiguration configuration = new DefaultContainerConfiguration()
                    .setName("maven-site-plugin")
                    .setClassWorld(world)
                    .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
                    .setAutoWiring(true);
            container = new DefaultPlexusContainer(configuration);
        } catch (Exception e) {
            throw new MojoException("Cannot start the Sisu container for the Doxia components", e);
        }
    }

    <T> T lookup(Class<T> type) {
        try {
            return container.lookup(type);
        } catch (ComponentLookupException e) {
            throw new MojoException("Cannot look up " + type.getName(), e);
        }
    }

    <T> java.util.Map<String, T> lookupMap(Class<T> type) {
        try {
            return container.lookupMap(type);
        } catch (ComponentLookupException e) {
            throw new MojoException("Cannot look up the components of " + type.getName(), e);
        }
    }

    @Override
    public void close() {
        container.dispose();
    }
}
