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

import java.util.Optional;

import org.apache.maven.api.Project;
import org.apache.maven.api.model.Site;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Tests for AbstractDeployMojo.
 */
public class AbstractDeployMojoTest {

    /**
     * Test that getTopLevelProject correctly handles SCM URLs with different repositories.
     * This is the test case for MSITE-1033.
     */
    @Test
    public void testGetTopLevelProjectWithDifferentScmUrls() throws Exception {
        // Create a mock deploy mojo
        TestDeployMojo mojo = new TestDeployMojo();

        // Create child project with SCM URL
        Project childProject =
                createProjectWithSite("child", "scm:git:git@github.com:codehaus-plexus/plexus-sec-dispatcher.git/");

        // Create parent project with different SCM URL
        Project parentProject =
                createProjectWithSite("parent", "scm:git:https://github.com/codehaus-plexus/plexus-pom.git/");

        // Set up the parent-child relationship
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        // Call getTopLevelProject - it should return childProject, not parentProject
        // because the SCM URLs point to different repositories
        Project topProject = mojo.getTopLevelProject(childProject);

        // The top project should be the child project itself since the parent has a different site
        assertEquals(childProject, topProject, "Top project should be child project due to different SCM URLs");
    }

    /**
     * Test that getTopLevelProject correctly handles SCM URLs with the same repository.
     */
    @Test
    public void testGetTopLevelProjectWithSameScmUrls() throws Exception {
        // Create a mock deploy mojo
        TestDeployMojo mojo = new TestDeployMojo();

        // Create child project with SCM URL
        Project childProject =
                createProjectWithSite("child", "scm:git:https://github.com/codehaus-plexus/plexus-pom.git/child");

        // Create parent project with same base SCM URL
        Project parentProject =
                createProjectWithSite("parent", "scm:git:https://github.com/codehaus-plexus/plexus-pom.git/");

        // Set up the parent-child relationship
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        // Call getTopLevelProject - it should return parentProject
        // because the SCM URLs point to the same repository
        Project topProject = mojo.getTopLevelProject(childProject);

        // The top project should be the parent project since they share the same site
        assertEquals(parentProject, topProject, "Top project should be parent project due to same SCM base URL");
    }

    /**
     * Test that getTopLevelProject correctly handles non-SCM URLs.
     */
    @Test
    public void testGetTopLevelProjectWithNonScmUrls() throws Exception {
        // Create a mock deploy mojo
        TestDeployMojo mojo = new TestDeployMojo();

        // Create child project with regular URL
        Project childProject = createProjectWithSite("child", "https://example.com/site/child");

        // Create parent project with same base URL
        Project parentProject = createProjectWithSite("parent", "https://example.com/site/");

        // Set up the parent-child relationship
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        // Call getTopLevelProject - it should return parentProject
        Project topProject = mojo.getTopLevelProject(childProject);

        // The top project should be the parent project since they share the same site
        assertEquals(parentProject, topProject, "Top project should be parent project for regular URLs");
    }

    /**
     * Test that getTopLevelProject correctly handles SCM URLs with standard https format.
     */
    @Test
    public void testGetTopLevelProjectWithHttpsScmUrls() throws Exception {
        // Create a mock deploy mojo
        TestDeployMojo mojo = new TestDeployMojo();

        // Create child project with https SCM URL
        Project childProject = createProjectWithSite("child", "scm:git:https://github.com/user/repo1.git/");

        // Create parent project with different https SCM URL (different domain)
        Project parentProject = createProjectWithSite("parent", "scm:git:https://gitlab.com/user/repo2.git/");

        // Set up the parent-child relationship
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        // Call getTopLevelProject - it should return childProject
        // because the SCM URLs point to different repositories
        Project topProject = mojo.getTopLevelProject(childProject);

        // The top project should be the child project itself since the parent has a different site
        assertEquals(childProject, topProject, "Top project should be child project due to different https SCM URLs");
    }

    /**
     * The reported case: sibling repositories on the same host. plexus-xml inherits from plexus-pom,
     * they share github.com, and their sites are unrelated.
     */
    @Test
    public void testGetTopLevelProjectWithSiblingRepositoriesOnSameHost() throws Exception {
        TestDeployMojo mojo = new TestDeployMojo();

        Project childProject =
                createProjectWithSite("child", "scm:git:https://github.com/codehaus-plexus/plexus-xml.git");
        Project parentProject =
                createProjectWithSite("parent", "scm:git:https://github.com/codehaus-plexus/plexus-pom.git");

        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        Project topProject = mojo.getTopLevelProject(childProject);

        assertEquals(childProject, topProject, "Sibling repositories do not share a site");
    }

    /**
     * Test that extractComparableUrl properly handles SVN URLs with different schemes but same host.
     * For SVN (hierarchical VCS), URLs with different schemes (http vs https) should be normalized
     * to the same scheme to allow site inheritance to recognize them as the same site.
     */
    @Test
    public void testExtractComparableUrlForSvnUrls() throws Exception {
        // Extract and normalize both URLs
        String url1 = AbstractDeployMojo.extractComparableUrl("scm:svn:http://svn.apache.org/repos/asf/maven/website");
        String url2 = AbstractDeployMojo.extractComparableUrl(
                "scm:svn:https://svn.apache.org/repos/asf/maven/website/components");

        // Both should be normalized to http scheme
        assertEquals("http://svn.apache.org/repos/asf/maven/website", url1);
        assertEquals("http://svn.apache.org/repos/asf/maven/website/components", url2);

        // Now verify they would be considered the same site by creating projects with these URLs
        TestDeployMojo mojo = new TestDeployMojo();
        Project childProject =
                createProjectWithSite("child", "scm:svn:https://svn.apache.org/repos/asf/maven/website/components");
        Project parentProject =
                createProjectWithSite("parent", "scm:svn:http://svn.apache.org/repos/asf/maven/website");
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        // The parent should be returned as the top project since they share the same SVN site
        Project topProject = mojo.getTopLevelProject(childProject);
        assertEquals(
                parentProject,
                topProject,
                "Top project should be parent due to normalized SVN URLs pointing to same site");
    }

    @Test
    public void testExtractComparableUrlWithBracketedIpv6() {
        String url = AbstractDeployMojo.extractComparableUrl("scm:git:git@[2001:db8::1]:user/repo.git");
        assertEquals("ssh://[2001:db8::1]/user/repo.git", url);
    }

    @Test
    public void testExtractComparableUrlWithMalformedScmUrl() {
        String malformedUrl = "scm:invalid";
        String result = AbstractDeployMojo.extractComparableUrl(malformedUrl);
        assertEquals(malformedUrl, result);
    }

    @Test
    public void testGetTopLevelProjectWithDifferentPorts() throws Exception {
        TestDeployMojo mojo = new TestDeployMojo();
        Project childProject = createProjectWithSite("child", "scm:git:https://example.com:8443/org/repo.git/child");
        Project parentProject = createProjectWithSite("parent", "scm:git:https://example.com:9443/org/repo.git/");
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        Project topProject = mojo.getTopLevelProject(childProject);
        assertEquals(childProject, topProject, "Projects with different ports should not share a site");
    }

    /**
     * MSITE-948: a parent with child.site.url.inherit.append.path="false" is not the top site of a child whose
     * site url lies elsewhere on the same host.
     */
    @Test
    public void testGetTopLevelProjectParentWithoutAppendPath() throws Exception {
        TestDeployMojo mojo = new TestDeployMojo();
        Project childProject = createProjectWithSite("child", "https://example.org/site/child");
        Project parentProject = createProjectWithoutAppendPath("parent", "https://example.org/site/base-parent");
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        assertEquals(childProject, mojo.getTopLevelProject(childProject));
    }

    @Test
    public void testGetTopLevelProjectWithAppendPathStillGroupsSiblings() throws Exception {
        TestDeployMojo mojo = new TestDeployMojo();
        Project childProject = createProjectWithSite("child", "https://example.org/site/child");
        Project parentProject = createProjectWithSite("parent", "https://example.org/site/base-parent");
        when(childProject.getParent()).thenReturn(Optional.of(parentProject));

        assertEquals(parentProject, mojo.getTopLevelProject(childProject));
    }

    @Test
    public void testGetTopLevelProjectWithoutAppendPathKeepsInheritedAndNestedUrls() throws Exception {
        TestDeployMojo mojo = new TestDeployMojo();
        Project parentProject = createProjectWithoutAppendPath("parent", "https://example.org/site/base-parent");

        Project inheritedUrl = createProjectWithSite("inherited", "https://example.org/site/base-parent");
        when(inheritedUrl.getParent()).thenReturn(Optional.of(parentProject));
        assertEquals(parentProject, mojo.getTopLevelProject(inheritedUrl));

        Project nestedUrl = createProjectWithSite("nested", "https://example.org/site/base-parent/nested");
        when(nestedUrl.getParent()).thenReturn(Optional.of(parentProject));
        assertEquals(parentProject, mojo.getTopLevelProject(nestedUrl));
    }

    private Project createProjectWithoutAppendPath(String artifactId, String siteUrl) {
        return DeployTestSupport.project(artifactId, siteUrl, null, false);
    }

    private Project createProjectWithSite(String artifactId, String siteUrl) {
        return DeployTestSupport.project(artifactId, siteUrl, null);
    }

    /**
     * Test implementation of AbstractDeployMojo for testing.
     */
    private static class TestDeployMojo extends AbstractDeployMojo {
        @Override
        protected boolean isDeploy() {
            return true;
        }

        @Override
        protected String determineTopDistributionManagementSiteUrl() {
            return "test";
        }

        @Override
        protected Site determineDeploySite() {
            return Site.newBuilder().id("test").url("test").build();
        }
    }
}
