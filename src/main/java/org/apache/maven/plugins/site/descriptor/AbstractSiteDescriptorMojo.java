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
package org.apache.maven.plugins.site.descriptor;

import java.io.File;
import java.util.Locale;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.doxia.site.SiteModel;
import org.apache.maven.doxia.site.inheritance.SiteModelInheritanceAssembler;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.doxia.tools.SiteToolException;
import org.apache.maven.plugins.site.AbstractSiteMojo;

/**
 * Abstract class to compute effective site model for site descriptors.
 *
 * @since 3.5
 */
public abstract class AbstractSiteDescriptorMojo extends AbstractSiteMojo {

    /**
     * Directory containing the <code>site.xml</code> file and the source for handwritten docs (one directory
     * per Doxia-source-supported markup types):
     * see <a href="/doxia/references/index.html">Doxia Markup Languages References</a>.
     *
     * @since 2.3
     */
    @Parameter(defaultValue = "${project.basedir}/src/site")
    protected File siteDirectory;

    /**
     * Make links in the site descriptor relative to the project URL.
     * By default, any absolute links that appear in the site descriptor,
     * e.g. banner hrefs, breadcrumbs, menu links, etc., will be made relative to project.url.
     * <p/>
     * Links will not be changed if this is set to false, or if the project has no URL defined.
     *
     * @since 2.3
     */
    @Parameter(property = "relativizeSiteLinks", defaultValue = "true")
    private boolean relativizeSiteLinks;

    /**
     * The component for assembling site model inheritance.
     */
    protected SiteModelInheritanceAssembler getAssembler() {
        return lookup(SiteModelInheritanceAssembler.class);
    }

    protected SiteModel prepareSiteModel(Locale locale) {
        SiteModel siteModel;
        try {
            siteModel = getSiteTool()
                    .getSiteModel(
                            siteDirectory,
                            locale,
                            getProjectWithParents(),
                            session.getProjects(),
                            session,
                            getRemoteProjectRepositories());
        } catch (SiteToolException e) {
            throw new MojoException("Failed to obtain site model", e);
        }

        if (relativizeSiteLinks) {
            final String url = project.getModel().getUrl();

            if (url == null) {
                getLog().warn("No project URL defined - site links will not be relativized!");
            } else {
                // MSITE-658
                final String localeUrl = !locale.equals(SiteTool.DEFAULT_LOCALE) ? append(url, locale.toString()) : url;

                getLog().info("Relativizing site links with respect to localized project URL: " + localeUrl);
                getAssembler().resolvePaths(siteModel, localeUrl);
            }
        }
        return siteModel;
    }

    private String append(String url, String path) {
        return url.endsWith("/") ? (url + path) : (url + '/' + path);
    }
}
