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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.FileUtils;
import java.util.List;
import java.io.File;
import java.io.IOException;

/**
 * Installs a Project Darkstar server
 * 
 * @goal install
 */
public class InstallMojo extends AbstractSgsMojo {
    
    /**
     * The groupId of the Project Darkstar server distribution.
     * Default value is com.projectdarkstar.server
     * 
     * @parameter default-value="com.projectdarkstar.server"
     * @required
     * @since 1.0-alpha-1
     */
    private String groupId;
    
    /**
     * The artifactId of the Project Darkstar server distribution.
     * Default value is sgs-server-dist.
     * 
     * @parameter default-value="sgs-server-dist"
     * @required
     * @since 1.0-alpha-1
     */
    private String artifactId;
    
    /**
     * The type of the Project Darkstar server distribution.
     * Default value is zip.
     * 
     * @parameter default-value="zip"
     * @since 1.0-alpha-1
     */
    private String type;
    
    /**
     * The classifier of the Project Darkstar server distribution.
     * Default is no classifier
     * 
     * @parameter
     * @since 1.0-alpha-1
     */
    private String classifier;
    
    /**
     * The version of the Project Darkstar server distribution.
     * 
     * @parameter
     * @required
     * @since 1.0-alpha-1
     */
    private String version;
    
    /**
     * The location to unpack and install the Project Darkstar server
     * distribution.  Default value is ${project.build.directory}
     * 
     * @parameter expression="${project.build.directory}"
     * @required
     * @since 1.0-alpha-1
     */
    private File outputDirectory;
    
    /**
     * If true, remove the given sgsHome directory which is presumably
     * the resulting installation after installing.  Otherwise, if a previous
     * installation exists, it is preserved, and the install is skipped.
     * 
     * @parameter default-value="false"
     * @since 1.0-alpha-2
     */
    private boolean cleanSgsHome;
    

    /** 
     * Component used for generating Maven Artifacts.
     * 
     * @component
     * @readonly
     * @required
     * @since 1.0-alpha-1
     */
    private ArtifactFactory artifactFactory;

    /** 
     * Componented used for resolving Maven Artifacts.
     * 
     * @component
     * @readonly
     * @required
     * @since 1.0-alpha-1
     */
    private ArtifactResolver resolver;

    /**
     * The local Maven repository.
     * 
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     * @since 1.0-alpha-1
     */
    private ArtifactRepository localRepository;

    /** 
     * The available remote Maven repositories.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}" 
     * @readonly
     * @required
     * @since 1.0-alpha-1
     */
    private List remoteRepositories;
    
    /**
     * Component used for acquiring unpacking utilities.
     *
     * @component
     * @readonly
     * @required
     * @since 1.0-alpha-1
     */
    protected ArchiverManager archiverManager;
    
    public void execute()
        throws MojoExecutionException {
        
        Artifact distribution = artifactFactory.createArtifactWithClassifier(
                    groupId, artifactId, version, type, classifier);
        
        //resolve dependency ensuring it is in local repository
        try {
            resolver.resolve(distribution, remoteRepositories, localRepository);
        } catch (ArtifactResolutionException are) {
            throw new MojoExecutionException("Unable to resolve artifact", are);
        } catch (ArtifactNotFoundException anfe) {
            throw new MojoExecutionException("Artifact not found", anfe);
        }

        //check that sgsHome exists
        if (sgsHome == null) {
            throw new MojoExecutionException(
                    "The sgsHome configuration parameter is not set!");
        }
        
        //check for previous installation
        if (sgsHome.exists() && sgsHome.isDirectory()) {
            if (cleanSgsHome) {
                try {
                    this.getLog().info("Removing previous Project Darkstar " +
                                       "installation at " + sgsHome);
                    FileUtils.deleteDirectory(sgsHome);
                } catch(IOException e) {
                    throw new MojoExecutionException("Unable to delete " + 
                                                     sgsHome, e);
                }
            } else {
                this.getLog().info("Previous Project Darkstar installation " +
                                   "found.  Skipping install.");
                return;
            }
        }
        
        //unpack dependency into target outputDirectory
        try {
            this.checkDirectory(outputDirectory);
            File f = distribution.getFile();
            
            this.getLog().info("Extracting " + f +
                               " into " + outputDirectory);
            UnArchiver unArchiver = archiverManager.getUnArchiver(f);
            unArchiver.setSourceFile(f);
            unArchiver.setDestDirectory(outputDirectory);
            unArchiver.extract();
        } catch (NoSuchArchiverException nsae) {
            throw new MojoExecutionException("Unknown archive", nsae);
        } catch (ArchiverException ae) {
            throw new MojoExecutionException("Error unpacking", ae);
        }
    }

}
