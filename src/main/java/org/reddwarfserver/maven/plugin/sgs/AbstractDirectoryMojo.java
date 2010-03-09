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
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.FileUtils;
import java.io.File;
import java.io.IOException;

/**
 * Abstract Mojo which provides common functionality to all Project Darkstar
 * Mojos that deploy artifacts into a directory.
 */
public abstract class AbstractDirectoryMojo extends AbstractSgsMojo {

    /**
     * If true, artifacts to deploy will be unpacked into the deploy directory.
     * Otherwise, they will simply be copied.  Defaults to "false"
     *
     * @parameter default-value="false"
     * @since 1.0-alpha-1
     */
    protected boolean unpack;

    /**
     * Component used for acquiring unpacking utilities.
     *
     * @component
     * @readonly
     * @required
     * @since 1.0-alpha-1
     */
    protected ArchiverManager archiverManager;

    public abstract File getDirectory() throws MojoExecutionException;

    public abstract File[] getFiles() throws MojoExecutionException;

    public void execute()
        throws MojoExecutionException
    {
        this.checkConfig();
        File directory = getDirectory();
        this.checkDirectory(directory);

        try {
            for (File f : this.getFiles()) {
                this.checkFile(f);
                if(unpack) {
                    this.getLog().info("Extracting " + f +
                                       " into " + directory);
                    UnArchiver unArchiver = archiverManager.getUnArchiver(f);
                    unArchiver.setSourceFile(f);
                    unArchiver.setDestDirectory(directory);
                    unArchiver.extract();
                } else {
                    this.getLog().info("Copying " + f +
                                       " to " + directory);
                    FileUtils.copyFileToDirectory(f, directory);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("File copy failed", e);
        } catch (NoSuchArchiverException nsae) {
            throw new MojoExecutionException("Unknown archive", nsae);
        } catch (ArchiverException ae) {
            throw new MojoExecutionException("Error unpacking", ae);
        }
    }
}

