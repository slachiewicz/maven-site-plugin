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

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.model.Reporting;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.doxia.tools.SiteTool;
import org.codehaus.plexus.i18n.I18N;

/**
 * Base class for site mojos.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public abstract class AbstractSiteMojo implements Mojo {
    /**
     * A comma separated list of locales to render. The first valid token will be the default Locale
     * for this site.
     *
     * @since 2.3
     */
    @Parameter(property = "locales", defaultValue = "default")
    private String locales;

    /**
     * Set this to 'true' to skip site generation and staging.
     *
     * @since 3.0
     */
    @Parameter(property = "maven.site.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * The Maven session.
     */
    @Inject
    protected Session session;

    /**
     * The Maven project.
     */
    @Inject
    protected Project project;

    @Inject
    private Log log;

    private SisuContainer container;

    protected Log getLog() {
        return log;
    }

    /**
     * ProjectManager is not bound for injection into a mojo, so it is taken from the session.
     */
    protected ProjectManager getProjectManager() {
        return session.getService(ProjectManager.class);
    }

    /**
     * Gets a component of the Doxia stack, Wagon or settings decryption, which are Sisu components.
     *
     * @param type the component role
     * @return the component
     */
    protected synchronized <T> T lookup(Class<T> type) {
        if (container == null) {
            container = new SisuContainer(getClass().getClassLoader());
        }
        return container.lookup(type);
    }

    protected synchronized <T> Map<String, T> lookupMap(Class<T> type) {
        if (container == null) {
            container = new SisuContainer(getClass().getClassLoader());
        }
        return container.lookupMap(type);
    }

    /**
     * SiteTool.
     */
    protected SiteTool getSiteTool() {
        return lookup(SiteTool.class);
    }

    /**
     * Internationalization.
     */
    protected I18N getI18n() {
        return lookup(I18N.class);
    }

    protected List<Locale> getLocales() {
        return getSiteTool().getSiteLocales(locales);
    }

    /**
     * The value of a POM property, such as <code>project.build.sourceEncoding</code>, which Maven 4 no longer
     * resolves as a parameter expression.
     *
     * @param key the property name
     * @return the value, or <code>null</code> if the POM does not define it
     */
    protected String getProjectProperty(String key) {
        return project.getModel().getProperties().get(key);
    }

    /**
     * The remote repositories of the project.
     */
    protected List<RemoteRepository> getRemoteProjectRepositories() {
        return getProjectManager().getRemoteProjectRepositories(project);
    }

    /**
     * The project with its parents, including those that are not in the reactor, see {@link ParentAwareProject}.
     *
     * @return the project
     */
    protected Project getProjectWithParents() {
        return ParentAwareProject.of(project, session, this::getRemoteProjectRepositories);
    }

    /**
     * The parent of a project, including a parent that is not in the reactor, which Maven 4
     * {@link Project#getParent()} does not return.
     *
     * @param child the project
     * @return the parent, if the POM declares one
     */
    protected Optional<Project> getParent(Project child) {
        return ParentAwareProject.of(child, session, this::getRemoteProjectRepositories)
                .getParent()
                .map(ParentAwareProject::unwrap);
    }

    /**
     * The project name, defaulting like Maven 3 to the artifactId if the POM has no name.
     *
     * @return the project name
     */
    protected String getProjectName() {
        String name = project.getModel().getName();
        return name != null ? name : project.getArtifactId();
    }

    /**
     * The directory the site is generated to, defaulting to <code>${project.build.directory}/site</code> because
     * Maven 4 leaves <code>${project.reporting.outputDirectory}</code> empty.
     *
     * @param configured the value of the <code>siteOutputDirectory</code> parameter, may be <code>null</code>
     * @return the output directory
     */
    protected File resolveOutputDirectory(File configured) {
        if (configured != null) {
            return configured;
        }
        Reporting reporting = project.getModel().getReporting();
        if (reporting != null && reporting.getOutputDirectory() != null) {
            return new File(reporting.getOutputDirectory());
        }
        return new File(project.getBuild().getDirectory(), "site");
    }

    @Override
    public final void execute() throws MojoException {
        try {
            doExecute();
        } catch (MojoException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoException(e.getMessage(), e);
        } finally {
            synchronized (this) {
                if (container != null) {
                    container.close();
                    container = null;
                }
            }
        }
    }

    protected abstract void doExecute() throws Exception;
}
