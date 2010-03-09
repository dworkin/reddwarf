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

import org.codehaus.plexus.util.FileUtils;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.junit.After;
import java.io.File;

/**
 * Test the {@code DeployDependenciesMojo} class
 */
@RunWith(JUnit4.class)
public class TestDeployDependenciesMojo extends AbstractTestSgsMojo {

    private static final String SINGLE_POM = "target/test-classes/unit/deploy-dependencies/config.xml";
    private File outputDirectory;

    private DeployDependenciesMojo mojo;
    private File sgsHome;
    private File deployDir;

    protected AbstractSgsMojo buildEmptyMojo() {
        return new DeployDependenciesMojo();
    }
    
    private void setup(String pom) throws Exception {
        mojo = this.lookupDummyMojo(DeployDependenciesMojo.class, "deploy-dependencies", pom);
        sgsHome = (File) this.getVariableValueFromObject(mojo, "sgsHome");
        deployDir = new File(sgsHome, "deploy");
        outputDirectory = new File(getBasedir(),
                                   "target" + File.separator +
                                   "test-classes" + File.separator +
                                   "unit" + File.separator +
                                   "deploy-dependencies");
        fillRepositories(mojo);
    }
    
    @After
    public void scrubHome() throws Exception {
        if (sgsHome != null && sgsHome.exists()) {
            FileUtils.deleteDirectory(sgsHome);
        }
    }

    @Test
    public void testExecuteSingleDependency() throws Exception {
        this.setup(SINGLE_POM);
        this.executeInstall(SINGLE_POM, outputDirectory);

        /*mojo.execute();
        Assert.assertTrue(deployDir.exists());
        Assert.assertTrue(deployDir.isDirectory());
        Assert.assertEquals(1, deployDir.listFiles().length);

        System.err.println(deployDir.listFiles()[0]);*/
    }

}

