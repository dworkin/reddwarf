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
import org.codehaus.plexus.util.FileUtils;
import java.io.File;
import java.io.IOException;

/**
 * Configures a Project Darkstar server installation by overlaying
 * sgs-boot.properties, sgs-server.properties, and sgs-logging.properties
 * files into the conf directory of the installation.
 *
 * @goal configure
 */
public class ConfigureMojo extends AbstractSgsMojo
{
    static final String CONF = "conf";
    static final String SGS_BOOT = "sgs-boot.properties";
    static final String SGS_SERVER = "sgs-server.properties";
    static final String SGS_LOGGING = "sgs-logging.properties";
    
    
    /**
     * The file used to override the sgs-boot.properties configuration
     * file of the Project Darkstar server.
     * 
     * @parameter
     * @since 1.0-alpha-1
     */
    private File sgsBoot;
    
    /**
     * The file used to override the sgs-server.properties configuration
     * file of the Project Darkstar server.
     * 
     * @parameter
     * @since 1.0-alpha-1
     */
    private File sgsServer;
    
    /**
     * The file used to override the sgs-logging.properties configuration
     * file of the Project Darkstar server.
     * 
     * @parameter
     * @since 1.0-alpha-1
     */
    private File sgsLogging;

    public void execute()
        throws MojoExecutionException
    {
        
        this.checkConfig();
        File confDirectory = new File(sgsHome, CONF);
        this.checkDirectory(confDirectory);

        try {
            if (sgsBoot != null) {
                this.checkFile(sgsBoot);
                File targetSgsBoot = new File(confDirectory, SGS_BOOT);
                this.getLog().info("Copying " + sgsBoot + 
                                   " to " + targetSgsBoot);
                FileUtils.copyFile(sgsBoot, targetSgsBoot);
            }

            if (sgsServer != null) {
                this.checkFile(sgsServer);
                File targetSgsServer = new File(confDirectory, SGS_SERVER);
                this.getLog().info("Copying " + sgsServer + 
                                   " to " + targetSgsServer);
                FileUtils.copyFile(sgsServer, targetSgsServer);
            }

            if (sgsLogging != null) {
                this.checkFile(sgsLogging);
                File targetSgsLogging = new File(confDirectory, SGS_LOGGING);
                this.getLog().info("Copying " + sgsLogging + 
                                   " to " + targetSgsLogging);
                FileUtils.copyFile(sgsLogging,
                                   new File(confDirectory, SGS_LOGGING));
            }
        } catch (IOException e) {
            throw new MojoExecutionException("File copy failed", e);
        }
    }

}
