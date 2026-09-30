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
package org.apache.maven.plugins.site.render;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.api.DownloadedArtifact;
import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.model.Reporting;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.doxia.site.Menu;
import org.apache.maven.doxia.site.MenuItem;
import org.apache.maven.doxia.site.SiteModel;
import org.apache.maven.doxia.siterenderer.DocumentRenderer;
import org.apache.maven.doxia.siterenderer.DocumentRenderingContext;
import org.apache.maven.doxia.siterenderer.RendererException;
import org.apache.maven.doxia.siterenderer.SiteRenderer;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext.SiteDirectory;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.doxia.tools.SiteToolException;
import org.apache.maven.plugins.site.descriptor.AbstractSiteDescriptorMojo;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.shared.archiver.MavenArchiver;

/**
 * Base class for site rendering mojos.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 *
 */
public abstract class AbstractSiteRenderingMojo extends AbstractSiteDescriptorMojo {
    /**
     * Module type exclusion mappings
     * ex: <code>fml  -> **&#47;*-m1.fml</code>  (excludes fml files ending in '-m1.fml' recursively)
     * <p/>
     * The configuration looks like this:
     * <pre>
     *   &lt;moduleExcludes&gt;
     *     &lt;moduleType&gt;filename1.ext,**&#47;*sample.ext&lt;/moduleType&gt;
     *     &lt;!-- moduleType can be one of 'apt', 'fml' or 'xdoc'. --&gt;
     *     &lt;!-- The value is a comma separated list of           --&gt;
     *     &lt;!-- filenames or fileset patterns.                   --&gt;
     *     &lt;!-- Here's an example:                               --&gt;
     *     &lt;xdoc&gt;changes.xml,navigation.xml&lt;/xdoc&gt;
     *   &lt;/moduleExcludes&gt;
     * </pre>
     */
    @Parameter
    private Map<String, String> moduleExcludes;

    /**
     * Additional template properties for rendering the site. See
     * <a href="/doxia/doxia-sitetools/doxia-site-renderer/">Doxia Site Renderer</a>.
     */
    @Parameter
    private Map<String, Object> attributes;

    /**
     * Directory containing generated documentation in source format (Doxia supported markup).
     * This is used to pick up other source docs that might have been generated at build time (by reports or any other
     * build time mean).
     * This directory is expected to have the same structure as <code>siteDirectory</code>
     * (ie. one directory per Doxia-source-supported markup types).
     * This is never editable.
     */
    @Parameter(alias = "workingDirectory", defaultValue = "${project.build.directory}/generated-site")
    protected File generatedSiteDirectory;

    /**
     * Optional list of source directories to be used instead of {@link #siteDirectory} for rendering edit URLs.
     * This is only used for documents rendered from {@link #siteDirectory} but not for those rendered from {@link AbstractSiteRenderingMojo#generatedSiteDirectory}.
     * as the latter ones are always considered non-editable.
     *
     * The first directory containing a document with the given relative path is used for generating the URL (the same relative path as the document has to {@link #siteDirectory}).
     * @since 3.22.0
     */
    @Parameter
    protected File[] alternativeSiteSourceDirectories;

    /**
     * The mojo execution
     */
    @Inject
    protected MojoExecution mojoExecution;

    /**
     * Whether to generate the summary page for project reports: project-info.html.
     * <p>
     * Not used: the Maven 4 API site plugin cannot run reports.
     *
     * @since 2.3
     */
    @Parameter(property = "generateProjectInfo", defaultValue = "true")
    private boolean generateProjectInfo;

    /**
     * Generate a sitemap. The result will be a "sitemap.html" file at the site root.
     *
     * @since 2.1
     */
    @Parameter(property = "generateSitemap", defaultValue = "false")
    private boolean generateSitemap;

    /**
     * Specifies the input encoding.
     *
     * @since 2.3
     */
    @Parameter(property = "encoding")
    private String inputEncoding;

    /**
     * Specifies the output encoding.
     *
     * @since 2.3
     */
    @Parameter(property = "outputEncoding")
    private String outputEncoding;

    /**
     * Timestamp for reproducible output archive entries, either formatted as ISO 8601
     * <code>yyyy-MM-dd'T'HH:mm:ssXXX</code> or as an int representing seconds since the epoch (like
     * <a href="https://reproducible-builds.org/docs/source-date-epoch/">SOURCE_DATE_EPOCH</a>).
     * Defaults to the <code>project.build.outputTimestamp</code> property.
     *
     * @since 3.9.0
     */
    @Parameter
    protected String outputTimestamp;

    /**
     * Whether to save Velocity processed Doxia content (<code>*.&lt;ext&gt;.vm</code>)
     * to <code>${generatedSiteDirectory}/processed</code>.
     *
     * @since 3.5
     */
    @Parameter
    private boolean saveProcessedContent;

    /**
     * Site renderer.
     */
    protected SiteRenderer getSiteRenderer() {
        return lookup(SiteRenderer.class);
    }

    /**
     * Gets the input files encoding.
     *
     * @return The input files encoding, never <code>null</code>.
     */
    protected String getInputEncoding() {
        String encoding = inputEncoding != null ? inputEncoding : getProjectProperty("project.build.sourceEncoding");
        return (encoding == null || encoding.isEmpty())
                ? Charset.defaultCharset().displayName()
                : encoding;
    }

    /**
     * Gets the effective reporting output files encoding.
     *
     * @return The effective reporting output file encoding, never <code>null</code>.
     */
    protected String getOutputEncoding() {
        String encoding =
                outputEncoding != null ? outputEncoding : getProjectProperty("project.reporting.outputEncoding");
        return encoding == null ? "UTF-8" : encoding;
    }

    /**
     * Gets the timestamp for reproducible output, from the parameter or the <code>project.build.outputTimestamp</code>
     * property.
     *
     * @return the timestamp, or <code>null</code> if not set
     */
    protected String getOutputTimestamp() {
        return outputTimestamp != null ? outputTimestamp : getProjectProperty("project.build.outputTimestamp");
    }

    protected void checkInputEncoding() {
        String encoding = inputEncoding != null ? inputEncoding : getProjectProperty("project.build.sourceEncoding");
        if (encoding == null || encoding.isEmpty()) {
            getLog().warn("Input file encoding has not been set, using platform encoding "
                    + Charset.defaultCharset().displayName() + ", i.e. build is platform dependent!");
        }
    }

    /**
     * Warns that reports are not generated. Maven 3 ran the configured report plugins, plus
     * maven-project-info-reports-plugin unless excluded, through maven-reporting-exec. Maven 4 API has no service
     * that can load another plugin, configure one of its mojos and call it, so there is nothing to run them with.
     *
     * @param generateReports the value of the <code>generateReports</code> parameter
     */
    protected void warnReportsNotSupported(boolean generateReports) {
        if (!generateReports) {
            return;
        }
        Reporting reporting = project.getModel().getReporting();
        int configured = reporting == null ? 0 : reporting.getPlugins().size();
        boolean defaults = reporting == null || !reporting.isExcludeDefaults();
        if (configured > 0 || defaults) {
            getLog().warn("Reports are not generated: the Maven 4 API offers no way to run a report mojo of another"
                    + " plugin. Skipping " + configured + " configured report plugin(s)"
                    + (defaults ? " and the default maven-project-info-reports-plugin" : "") + ".");
        }
    }

    protected SiteRenderingContext createSiteRenderingContext(Locale locale) throws IOException {
        SiteModel siteModel = prepareSiteModel(locale);
        Map<String, Object> templateProperties = new HashMap<>();
        // skin templates read $project.name, $project.url, ...: the immutable model has those getters
        templateProperties.put("project", getProjectWithParents().getModel());
        templateProperties.put("inputEncoding", getInputEncoding());
        templateProperties.put("outputEncoding", getOutputEncoding());

        // Put any of the properties in directly into the Velocity context
        for (Map.Entry<String, String> entry :
                project.getModel().getProperties().entrySet()) {
            templateProperties.put(entry.getKey(), entry.getValue());
        }

        // Comes last if someone wants to deliberately override any default or model properties
        if (attributes != null) {
            templateProperties.putAll(attributes);
        }

        if (siteModel.getSkin() == null) {
            File siteDescriptor = new File(siteDirectory, "site.xml");
            throw new MojoException("No skin is declared in the site descriptor. Since the site descriptor"
                    + " no longer provides a default skin, a skin must be declared explicitly, for example in "
                    + siteDescriptor + ":" + System.lineSeparator()
                    + "  <skin>" + System.lineSeparator()
                    + "    <groupId>org.apache.maven.skins</groupId>" + System.lineSeparator()
                    + "    <artifactId>maven-fluido-skin</artifactId>" + System.lineSeparator()
                    + "    <version>...</version>" + System.lineSeparator()
                    + "  </skin>" + System.lineSeparator()
                    + "The skin is normally inherited from the Maven parent's site descriptor; check that the parent"
                    + " site descriptor is resolvable if you expected it to be inherited.");
        }

        SiteRenderingContext context;
        try {
            DownloadedArtifact skinArtifact = getSiteTool()
                    .getSkinArtifactFromRepository(session, getRemoteProjectRepositories(), siteModel.getSkin());

            context = getSiteRenderer()
                    .createContextForSkin(
                            session, skinArtifact, templateProperties, siteModel, getProjectName(), locale);
        } catch (SiteToolException e) {
            throw new MojoException("Failed to retrieve skin artifact from repository", e);
        } catch (RendererException e) {
            throw new MojoException("Failed to create context for skin", e);
        }

        // Add publish date
        MavenArchiver.parseBuildOutputTimestamp(getOutputTimestamp()).ifPresent(v -> {
            context.setPublishDate(Date.from(v));
        });

        // Generate static site
        context.setRootDirectory(project.getBasedir().toFile());
        final SiteDirectory mainSiteDirectory;
        if (!locale.equals(SiteTool.DEFAULT_LOCALE)) {
            mainSiteDirectory = new SiteDirectory(new File(this.siteDirectory, locale.toString()), true);
            context.addSiteDirectory(new SiteDirectory(new File(generatedSiteDirectory, locale.toString()), false));
        } else {
            mainSiteDirectory = new SiteDirectory(siteDirectory, true);
            context.addSiteDirectory(new SiteDirectory(generatedSiteDirectory, false));
        }
        if (alternativeSiteSourceDirectories != null) {
            for (File additionalSiteSourceDirectory : alternativeSiteSourceDirectories) {
                mainSiteDirectory.addAlternativeEditableSourceDirectory(additionalSiteSourceDirectory);
            }
        }
        context.addSiteDirectory(mainSiteDirectory);

        if (moduleExcludes != null) {
            context.setModuleExcludes(moduleExcludes);
        }

        if (saveProcessedContent) {
            File processedDir = new File(generatedSiteDirectory, "processed");
            if (!locale.equals(SiteTool.DEFAULT_LOCALE)) {
                context.setProcessedContentOutput(new File(processedDir, locale.toString()));
            } else {
                context.setProcessedContentOutput(processedDir);
            }
        }

        return context;
    }

    /**
     * Locate every document to be rendered for given locale:<ul>
     * <li>handwritten content, ie Doxia files,</li>
     * <li>the sitemap, if requested.</li>
     * </ul>
     * The report documents and the "Project Information" and "Project Reports" category summaries of the Maven 3
     * site plugin are not located: no report can run, see {@link #warnReportsNotSupported(boolean)}.
     *
     * @param context the site context
     * @param locale the locale
     * @return the documents and their renderers
     * @throws IOException in case of file reading issue
     * @throws RendererException in case of Doxia rendering issue
     */
    protected Map<String, DocumentRenderer> locateDocuments(SiteRenderingContext context, Locale locale)
            throws IOException, RendererException {
        Map<String, DocumentRenderer> documents = getSiteRenderer().locateDocumentFiles(context);

        // there are no reports: this drops the "reports" menu reference, like a Maven 3 site without reports
        getSiteTool().populateReportsMenu(context.getSiteModel(), locale, Map.<String, List<MavenReport>>of());
        populateReportItems(context.getSiteModel());

        File localizedSiteDirectory;
        if (!locale.equals(SiteTool.DEFAULT_LOCALE)) {
            localizedSiteDirectory = new File(siteDirectory, locale.toString());
        } else {
            localizedSiteDirectory = siteDirectory;
        }

        if (generateSitemap) {
            org.apache.maven.api.plugin.descriptor.PluginDescriptor plugin =
                    mojoExecution.getPlugin().getDescriptor();
            String generator =
                    plugin.getGroupId() + ':' + plugin.getArtifactId() + ':' + plugin.getVersion() + ":sitemap";
            DocumentRenderingContext docRenderingContext =
                    new DocumentRenderingContext(localizedSiteDirectory, "sitemap", generator);
            String title = getI18n().getString("site-plugin", locale, "site.sitemap.title");
            DocumentRenderer docRenderer = new SitemapDocumentRenderer(
                    plugin.getArtifactId() + ':' + plugin.getVersion() + ":sitemap",
                    docRenderingContext,
                    title,
                    context.getSiteModel(),
                    getI18n(),
                    getLog());

            String filename = docRenderer.getOutputName();
            if (!documents.containsKey(filename)) {
                documents.put(filename, docRenderer);
            } else {
                getLog().info("Skipped \"" + title + "\" report; file \"" + filename + "\" already exists.");
            }
        }

        return documents;
    }

    protected void populateReportItems(SiteModel siteModel) {
        for (Menu menu : siteModel.getMenus()) {
            populateItemRefs(menu.getItems());
        }
    }

    private void populateItemRefs(List<MenuItem> items) {
        for (Iterator<MenuItem> i = items.iterator(); i.hasNext(); ) {
            MenuItem item = i.next();

            if (item.getRef() != null) {
                getLog().warn("Unrecognised reference: '" + item.getRef() + "'");
                i.remove();
            }

            populateItemRefs(item.getItems());
        }
    }
}
