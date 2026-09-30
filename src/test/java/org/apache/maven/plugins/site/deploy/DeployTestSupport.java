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
package org.apache.maven.plugins.site.deploy;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.DistributionManagement;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Site;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.settings.Proxy;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.plugins.site.AbstractSiteMojo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds the Maven 4 objects the deploy mojos read, without a Maven session.
 */
final class DeployTestSupport {

    private DeployTestSupport() {}

    static Project project(String artifactId, String siteUrl, Project parent) {
        Model model = Model.newBuilder()
                .groupId("org.apache.maven.test")
                .artifactId(artifactId)
                .version("1.0")
                .name(artifactId)
                .distributionManagement(DistributionManagement.newBuilder()
                        .site(Site.newBuilder().id("test-site").url(siteUrl).build())
                        .build())
                .build();
        Project project = mock(Project.class);
        when(project.getModel()).thenReturn(model);
        when(project.getGroupId()).thenReturn(model.getGroupId());
        when(project.getArtifactId()).thenReturn(artifactId);
        when(project.getVersion()).thenReturn("1.0");
        when(project.getParent()).thenReturn(Optional.ofNullable(parent));
        return project;
    }

    static Session session(Proxy... proxies) {
        Session session = mock(Session.class);
        when(session.getSettings())
                .thenReturn(Settings.newBuilder().proxies(List.of(proxies)).build());
        return session;
    }

    /**
     * Sets the fields Maven 4 DI and parameter injection would set.
     */
    static void prepare(AbstractSiteMojo mojo, Project project, Session session) throws Exception {
        set(mojo, "project", project);
        set(mojo, "session", session);
        set(mojo, "log", mock(Log.class));
    }

    static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
