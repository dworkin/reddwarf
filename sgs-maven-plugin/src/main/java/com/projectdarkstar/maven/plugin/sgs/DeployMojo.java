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

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Deploys a jar or jar files into a Project Darkstar server installation.
 *
 * @goal deploy
 */
public class DeployMojo extends AbstractSgsMojo {
    
    private static String DEPLOY = "deploy";
    
    /**
     * The deploy directory of the Project Darkstar installation.
     * Defaults to the "deploy" subdirectory under sgsHome.
     * 
     * @parameter
     */
    private File deployDir;
    
    /**
     * The jar files to deploy into the Project Darkstar server.
     */
    private File[] files;
    
    public void execute()
        throws MojoExecutionException
    {
        this.checkConfig();
        if(deployDir == null) {
            deployDir = new File(sgsHome, DEPLOY);
        }
        this.checkDirectory(deployDir);
        
        try {
            for (File f : files) {
                this.checkFile(f);
                FileUtils.copyFileToDirectory(f, deployDir);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("File copy failed", e);
        }
    }
}
