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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.settings.Proxy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiteDeployWebDavWithoutAuthTest {
    @TempDir
    private static File directory;

    private static File siteTargetPath;
    private static SimpleDavServerHandler simpleDavServerHandler;

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

    @BeforeAll
    public static void prepare() throws Exception {
        siteTargetPath = new File(newFolder(directory, "junit"), "target");
        if (!siteTargetPath.exists()) {
            siteTargetPath.mkdirs();
        }
        simpleDavServerHandler = new SimpleDavServerHandler(siteTargetPath);
    }

    @AfterEach
    public void cleanup() throws Exception {
        simpleDavServerHandler.httpRequests.clear();
        FileUtils.cleanDirectory(siteTargetPath);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        simpleDavServerHandler.stop();
    }

    @Nested
    class NoAutzDavDeploy {

        @Test
        public void noAuthzDavDeploy() throws Exception {
            assertTrue(
                    simpleDavServerHandler.getPort() > 0,
                    "dav server port not available: " + simpleDavServerHandler.getPort());

            Project project = DeployTestSupport.project(
                    "deploy-dav", "dav:http://localhost:" + simpleDavServerHandler.getPort() + "/site/", null);

            deploy(project, DeployTestSupport.session());

            assertContentInFiles();
            assertFalse(assertAtLeastOneRequestContainsHeader(simpleDavServerHandler.httpRequests, "proxy-connection"));
        }
    }

    @Nested
    class DavDeployThruProxyWithoutAuthzInProxy {

        @Test
        public void davDeployThruProxyWithoutAuthzInProxy() throws Exception {
            // olamy, Note : toto is something like foo or bar for french folks :-)
            Project project = DeployTestSupport.project("deploy-dav", "dav:http://toto.com/site/", null);

            deploy(project, DeployTestSupport.session(proxy()));

            assertContentInFiles();

            assertTrue(assertAtLeastOneRequestContainsHeader(simpleDavServerHandler.httpRequests, "proxy-connection"));
        }

        private Proxy proxy() {
            // dummy proxy
            return Proxy.newBuilder()
                    .activeString("true")
                    .host("localhost")
                    .portString(String.valueOf(simpleDavServerHandler.getPort()))
                    .protocol("http")
                    .nonProxyHosts("www.google.com|*.somewhere.com")
                    .build();
        }
    }

    private static void deploy(Project project, Session session) throws Exception {
        SiteDeployMojo mojo = new SiteDeployMojo();
        DeployTestSupport.prepare(mojo, project, session);
        DeployTestSupport.set(mojo, "inputDirectory", new File("src/test/resources/unit/deploy-dav/target/site"));
        DeployTestSupport.set(mojo, "chmod", false);
        mojo.execute();
    }

    private void assertContentInFiles() throws Exception {
        File htmlFile = new File(siteTargetPath, "site" + File.separator + "index.html");
        assertTrue(htmlFile.exists());
        String htmlContent = new String(Files.readAllBytes(htmlFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(htmlContent.contains("Welcome to Apache Maven"));

        File cssFile = new File(siteTargetPath, "site" + File.separator + "css" + File.separator + "maven-base.css");
        assertTrue(cssFile.exists());
        String cssContent = new String(Files.readAllBytes(cssFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(cssContent.contains("background-image: url(../images/collapsed.gif);"));
    }

    private boolean assertAtLeastOneRequestContainsHeader(List<HttpRequest> requests, String headerName) {
        for (HttpRequest rq : requests) {
            boolean containsProxyHeader = rq.headers.containsKey(headerName);
            if (containsProxyHeader) {
                return true;
            }
        }
        return false;
    }
}
