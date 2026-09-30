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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DownloadedArtifact;
import org.apache.maven.api.Language;
import org.apache.maven.api.Packaging;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.services.ProjectBuilder;
import org.apache.maven.api.services.ProjectBuilderRequest;
import org.apache.maven.api.services.ProjectBuilderResult;

/**
 * A project that also knows its parents when they are not in the reactor.
 * <p>
 * Maven 3 <code>MavenProject.getParent()</code> built the parent from the repository when it was not in the reactor,
 * and the site descriptor and the distribution management were inherited along that chain. Maven 4
 * {@link Project#getParent()} is empty for such a parent, so a site descriptor inherited from a repository parent
 * (for example the skin declared by <code>org.apache:apache</code> or <code>maven-parent</code>) would be lost.
 * This class builds the missing parent from its POM in the repository. As in Maven 3, a parent that comes from the
 * repository has no base directory, which tells the Doxia site tools to look for its site descriptor in the
 * repository as well.
 */
final class ParentAwareProject implements Project {

    private final Project delegate;

    private final boolean fromRepository;

    private final Session session;

    private final Supplier<List<RemoteRepository>> repositories;

    private Optional<Project> parent;

    private ParentAwareProject(
            Project delegate, boolean fromRepository, Session session, Supplier<List<RemoteRepository>> repositories) {
        this.delegate = delegate;
        this.fromRepository = fromRepository;
        this.session = session;
        this.repositories = repositories;
    }

    /**
     * @param project the project
     * @param session the session, used to resolve and build parents that are not in the reactor
     * @param repositories the repositories to resolve parents from, asked for only if a parent has to be built
     * @return the project with a complete chain of parents
     */
    static Project of(Project project, Session session, Supplier<List<RemoteRepository>> repositories) {
        return project instanceof ParentAwareProject
                ? project
                : new ParentAwareProject(project, false, session, repositories);
    }

    /**
     * @param project a project, possibly an instance of this class
     * @return the project as Maven gave it, or as built from the repository
     */
    static Project unwrap(Project project) {
        return project instanceof ParentAwareProject ? ((ParentAwareProject) project).delegate : project;
    }

    @Override
    public synchronized Optional<Project> getParent() {
        if (parent == null) {
            Optional<Project> inReactor = delegate.getParent();
            if (inReactor.isPresent()) {
                parent = Optional.of(new ParentAwareProject(inReactor.get(), false, session, repositories));
            } else {
                Parent declared = delegate.getModel().getParent();
                parent = declared == null
                        ? Optional.empty()
                        : Optional.of(
                                new ParentAwareProject(buildFromRepository(declared), true, session, repositories));
            }
        }
        return parent;
    }

    private Project buildFromRepository(Parent declared) {
        try {
            Artifact pom = session.createArtifact(
                    declared.getGroupId(), declared.getArtifactId(), declared.getVersion(), "pom");
            List<RemoteRepository> remoteRepositories = repositories.get();
            DownloadedArtifact downloaded = session.resolveArtifact(pom, remoteRepositories);
            ProjectBuilderResult result = session.getService(ProjectBuilder.class)
                    .build(ProjectBuilderRequest.builder()
                            .session(session)
                            .path(downloaded.getPath())
                            .processPlugins(false)
                            .repositories(remoteRepositories)
                            .build());
            return result.getProject()
                    .orElseThrow(() -> new MojoException("Cannot build the parent project " + declared.getGroupId()
                            + ':' + declared.getArtifactId() + ':' + declared.getVersion()));
        } catch (MojoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MojoException(
                    "Cannot build the parent project " + declared.getGroupId() + ':' + declared.getArtifactId() + ':'
                            + declared.getVersion() + " from the repository",
                    e);
        }
    }

    /**
     * @return <code>null</code> for a parent from the repository, like a Maven 3 project built from a repository POM
     */
    @Override
    public Path getBasedir() {
        return fromRepository ? null : delegate.getBasedir();
    }

    @Override
    public String getGroupId() {
        return delegate.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return delegate.getArtifactId();
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public Packaging getPackaging() {
        return delegate.getPackaging();
    }

    @Override
    public Language getLanguage() {
        return delegate.getLanguage();
    }

    @Override
    public ProducedArtifact getPomArtifact() {
        return delegate.getPomArtifact();
    }

    @Override
    public Optional<ProducedArtifact> getMainArtifact() {
        return delegate.getMainArtifact();
    }

    @Override
    public List<ProducedArtifact> getArtifacts() {
        return delegate.getArtifacts();
    }

    /**
     * @return the model, with the artifactId as name if the POM has none, like Maven 3
     *         <code>MavenProject.getName()</code>; the site tools read the name from the model
     */
    @Override
    public Model getModel() {
        Model model = delegate.getModel();
        return model.getName() == null ? model.withName(delegate.getArtifactId()) : model;
    }

    @Override
    public Path getPomPath() {
        return delegate.getPomPath();
    }

    @Override
    public List<DependencyCoordinates> getDependencies() {
        return delegate.getDependencies();
    }

    @Override
    public List<DependencyCoordinates> getManagedDependencies() {
        return delegate.getManagedDependencies();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public boolean isTopProject() {
        return delegate.isTopProject();
    }

    @Override
    public boolean isRootProject() {
        return delegate.isRootProject();
    }

    @Override
    public Path getRootDirectory() {
        return delegate.getRootDirectory();
    }

    @Override
    public List<Profile> getDeclaredProfiles() {
        return delegate.getDeclaredProfiles();
    }

    @Override
    public List<Profile> getEffectiveProfiles() {
        return delegate.getEffectiveProfiles();
    }

    @Override
    public List<Profile> getDeclaredActiveProfiles() {
        return delegate.getDeclaredActiveProfiles();
    }

    @Override
    public List<Profile> getEffectiveActiveProfiles() {
        return delegate.getEffectiveActiveProfiles();
    }
}
