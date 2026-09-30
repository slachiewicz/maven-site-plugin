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

import java.io.File;

import org.apache.maven.api.Project;
import org.apache.maven.api.model.Site;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

/**
 * Deploys the generated site to a local staging or mock directory based on the site URL
 * specified in the <code>&lt;distributionManagement&gt;</code> section of the
 * POM.
 * <p>It can be used to test that links between module sites in a multi-module
 * build work.
 * </p>
 * <p>This goal requires the site to already have been generated using the site goal,
 * such as by calling <code>mvn site</code>.</p>
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 *
 * @since 2.0
 */
@Mojo(name = "stage")
public class SiteStageMojo extends AbstractStagingMojo {
    /**
     * Staging directory location. This needs to be an absolute path, like
     * <code>C:\stagingArea\myProject\</code> on Windows or
     * <code>/stagingArea/myProject/</code> on Unix.
     * If this is not specified, the site will be staged in ${project.build.directory}/staging.
     *
     * @since 2.3
     */
    @Parameter(property = "stagingDirectory")
    private File stagingDirectory;

    @Override
    protected void doExecute() {
        if (skip) {
            getLog().info("maven.site.skip = true: Skipping site staging");
            return;
        }

        super.doExecute();
    }

    @Override
    protected boolean isDeploy() {
        return false;
    }

    @Override
    protected Site determineDeploySite() throws MojoException {
        final File outputDirectory = determineStagingDirectory();
        getLog().info("Using this base directory for staging: " + outputDirectory);

        // Safety
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        return Site.newBuilder()
                .id("stagingLocal")
                .url("file://" + outputDirectory.getAbsolutePath())
                .build();
    }

    /**
     * Find the directory where staging will take place.
     *
     * @return the directory for staging
     */
    private File determineStagingDirectory() {
        if (stagingDirectory != null) {
            // the user has specified a stagingDirectory - use it
            getLog().debug("stagingDirectory specified by the user: " + stagingDirectory);
            return stagingDirectory;
        }

        // The user didn't specify a URL: calculate default in the execution root target dir
        File defaultStagingDirectory = new File(getExecutionRootBuildDirectory(), DEFAULT_STAGING_DIRECTORY);
        getLog().debug("stagingDirectory NOT specified, using the execution root project: " + defaultStagingDirectory);
        return defaultStagingDirectory;
    }

    /**
     * Find the build directory of the execution root project in the reactor.
     * If no execution root project is found, the build directory of the current project is returned.
     *
     * @return the build directory of the execution root project.
     */
    protected File getExecutionRootBuildDirectory() {
        // Find the top level project in the reactor
        final Project executionRootProject = session.getProjects().stream()
                .filter(Project::isTopProject)
                .findFirst()
                .orElse(null);

        // Use the top level project's build directory if there is one, otherwise use this project's build directory
        final File buildDirectory;

        if (executionRootProject == null) {
            getLog().debug("No execution root project found in the reactor, using the current project.");

            buildDirectory = new File(project.getBuild().getDirectory());
        } else {
            getLog().debug("Using the execution root project found in the reactor: "
                    + executionRootProject.getArtifactId());

            buildDirectory = new File(executionRootProject.getBuild().getDirectory());
        }

        return buildDirectory;
    }
}
