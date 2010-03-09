/*
 * Copyright (c) 2009, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.reddwarfserver.maven.plugin.sgs;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.StringUtils;
import java.io.File;
import java.util.Set;
import java.util.Iterator;
import java.util.List;

/**
 * Deploys an extension jar or jar files into a Project Darkstar server
 * installation.
 *
 * @goal extend-dependencies
 */
public class ExtendDependenciesMojo extends AbstractExtendMojo {

    /**
     * The Maven project POM
     *
     * @parameter expression="${project}"
     * @readonly
     * @required
     * @since 1.0-beta-1
     */
    protected MavenProject project;


    /**
     * A list of artifactIds from the project's dependencies to deploy
     * into the server installation.  By default, all project dependencies
     * are deployed.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String[] includeArtifactIds;

    /**
     * A list of classifiers to include.  By default, all classifiers are
     * included.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String[] includeClassifiers;

    /**
     * A list of types to include.  By default, all types are included.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String[] includeTypes;

    /**
     * A single scope identifier.  Only artifacts from the given scope are
     * included.  If both the includeScope and the excludeScope configuration
     * properties are set, the excludeScope property will be ignored.  By
     * default, only artifacts that satisfy the compile scope are included.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String includeScope;

    /**
     * A list of artifactIds from the project's dependencies to exclude
     * from the deployment list.  By default, all project dependencies
     * are deployed.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String[] excludeArtifactIds;

    /**
     * A list of classifiers to exclude.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String[] excludeClassifiers;

    /**
     * A list of types to exclude.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String[] excludeTypes;

    /**
     * A single scope identifier.  All artifacts from the given scope are
     * excluded.  If both the includeScope and the excludeScope configuration
     * properties are set, the excludeScope property will be ignored.
     *
     * @parameter
     * @since 1.0-beta-1
     */
    private String excludeScope;

    /**
     * Exclude transitive dependencies.  Default value is false.
     *
     * @parameter default-value="false"
     * @since 1.0-beta-1
     */
    private boolean excludeTransitive;

    /**
     * If true, any dependencies that are transitive dependencies whose
     * non-transitive parent dependencies have been excluded,
     * should also be excluded.
     *
     * @parameter default-value="true"
     * @since 1.0-beta-1
     */
    private boolean excludeOrphanTransitive;

    /**
     * Componented used for resolving Maven Artifacts.
     *
     * @component
     * @readonly
     * @required
     * @since 1.0-beta-1
     */
    private ArtifactResolver resolver;

    /**
     * The local Maven repository.
     *
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     * @since 1.0-beta-1
     */
    private ArtifactRepository localRepository;

    /**
     * The available remote Maven repositories.
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @readonly
     * @required
     * @since 1.0-beta-1
     */
    private List remoteRepositories;

    /**
     * Component used to resolve artifacts.
     *
     * @component
     * @readonly
     * @required
     * @since 1.0-beta-1
     */
    private ArtifactMetadataSource artifactMetadataSource;

    public File[] getFiles() throws MojoExecutionException {

        FilterArtifacts filter = buildFilter();
        Set baseArtifacts = null;
        if(excludeOrphanTransitive) {
            Set dependencyArtifacts = project.getDependencyArtifacts();
            try {
                ArtifactResolutionResult result =
                        resolver.resolveTransitively(filter.filter(dependencyArtifacts),
                                                     project.getArtifact(),
                                                     remoteRepositories,
                                                     localRepository,
                                                     artifactMetadataSource);
                baseArtifacts = result.getArtifacts();
            } catch (ArtifactResolutionException are) {
                throw new MojoExecutionException("Failure resolving dependencies", are);
            } catch (ArtifactNotFoundException anfe) {
                throw new MojoExecutionException("Failure to locate artifact", anfe);
            } catch (ArtifactFilterException afe) {
                throw new MojoExecutionException("Failture filtering artifacts", afe);
            }
        } else {
            baseArtifacts = project.getArtifacts();
        }

        try {

            Set artifacts = filter.filter(baseArtifacts);
            File[] files = new File[artifacts.size()];

            Iterator a = artifacts.iterator();
            for(int i = 0; a.hasNext(); i++) {
                files[i] = ((Artifact) a.next()).getFile();
            }

            return files;
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException("Failure filtering artifacts", e);
        }

    }

    private FilterArtifacts buildFilter() {
        //default to using dependencies with scope compile
        if(includeScope == null && excludeScope == null) {
            includeScope = "compile";
        }

        FilterArtifacts f = new FilterArtifacts();
        f.addFilter(new ArtifactIdFilter(safeJoin(includeArtifactIds, ","),
                                         safeJoin(excludeArtifactIds, ",")));
        f.addFilter(new ClassifierFilter(safeJoin(includeClassifiers, ","),
                                         safeJoin(excludeClassifiers, ",")));
        f.addFilter(new TypeFilter(safeJoin(includeTypes, ","),
                                   safeJoin(excludeTypes, ",")));
        f.addFilter(new ScopeFilter(includeScope,
                                    excludeScope));
        f.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(),
                                                  excludeTransitive));

        return f;
    }

    private String safeJoin(String[] strings, String delimiter) {
        if(strings == null) {
            return "";
        } else {
            return StringUtils.join(strings, delimiter);
        }
    }
}

