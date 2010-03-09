/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a BSD-style license that can be found
 * in the LICENSE file.
 */
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
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.StreamConsumer;
import java.io.File;

/**
 * Stops a RedDwarf server installation.
 * 
 * @goal stop
 */
public class StopMojo extends AbstractSgsMojo {
    
    static final String BIN = "bin";
    static final String STOPJAR = "sgs-stop.jar";
    
    /**
     * Optional parameter to specify a boot properties file to feed on the
     * command line.  By default, the sgs-boot.properties file from the
     * RedDwarf server installation conf directory is used.
     * 
     * @parameter
     * @since 1.0-alpha-1
     */
    private File alternateBoot;
    
    public void execute()
        throws MojoExecutionException
    {
        
        this.checkConfig();
        File binDir = new File(sgsHome, BIN);
        this.checkDirectory(binDir);
        File stopJar = new File(binDir, STOPJAR);
        this.checkFile(stopJar);

        //prepare the java command
        Commandline command = new Commandline();
        String javaCmd = System.getProperty("java.home") + File.separator + 
                "bin" + File.separator + "java";
        command.setExecutable(javaCmd);
        command.addArguments(new String[]{"-jar", stopJar.getAbsolutePath()});
        
        //add the boot properties argument if it is specified
        if(alternateBoot != null) {
            this.checkFile(alternateBoot);
            command.addArguments(new String[]{alternateBoot.getAbsolutePath()});
        }
        
        //launch
        try {
            LogStreamConsumer l = new LogStreamConsumer();
            CommandLineUtils.executeCommandLine(command, l, l);
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Unable to stop server", e);
        }
    }

    private class LogStreamConsumer implements StreamConsumer {

        public void consumeLine(String line) {
            StopMojo.this.getLog().info(line);
        }
        
    }
}
