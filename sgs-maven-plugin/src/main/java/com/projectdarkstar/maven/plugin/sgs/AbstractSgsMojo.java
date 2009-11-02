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

package com.projectdarkstar.maven.plugin.sgs;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.File;

/**
 * Abstract Mojo which provides common functionality to all Project Darkstar
 * Mojos.
 */
public abstract class AbstractSgsMojo extends AbstractMojo {
    
    /**
     * Directory where the Project Darkstar server is installed.
     * 
     * @parameter
     * @required
     * @since 1.0-alpha-1
     */
    protected File sgsHome;

    protected void checkConfig() throws MojoExecutionException {
        if (sgsHome == null) {
            throw new MojoExecutionException(
                    "The sgsHome configuration parameter is not set!");
        } else if(!sgsHome.exists()) {
            throw new MojoExecutionException(
                    "The directory specified by sgsHome does not exist : " + 
                    sgsHome);
        }
    }
    
    protected void checkDirectory(File dir) throws MojoExecutionException {
        if(dir == null || !dir.exists() || !dir.isDirectory()) {
            throw new MojoExecutionException(
                    "Directory does not exist or is not a directory : " + dir);
        }
    }
    
    protected void checkFile(File file) throws MojoExecutionException {
        if(file == null || !file.exists() || !file.isFile()) {
            throw new MojoExecutionException(
                    "File does not exist or is not a file : " + file);
        }
    }
    
}
