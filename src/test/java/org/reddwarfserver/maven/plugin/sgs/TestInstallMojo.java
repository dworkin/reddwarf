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
import org.codehaus.plexus.util.FileUtils;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.junit.Assert;
import java.io.File;

/**
 * Test the {@code InstallMojo} class
 */
@RunWith(JUnit4.class)
public class TestInstallMojo extends AbstractTestSgsMojo {

    protected AbstractSgsMojo buildEmptyMojo() throws Exception {
        return new InstallMojo();
    }

    private InstallMojo lookupDummyMojo(String pom) throws Exception {
        return super.lookupDummyMojo(InstallMojo.class, "install", pom);
    }

    @Test
    public void testLookup() throws Exception {
        InstallMojo mojo = lookupDummyMojo("target/test-classes/unit/install/config.xml");
        Assert.assertNotNull(mojo);
    }

    @Test
    public void testExecute() throws Exception {
        InstallMojo mojo = lookupDummyMojo("target/test-classes/unit/install/config.xml");
        Assert.assertNotNull(mojo);

        fillDefaultValues(mojo);
        fillRepositories(mojo);
        mojo.execute();
        
        File sgsHome = (File) this.getVariableValueFromObject(mojo, "sgsHome");
        System.err.println(sgsHome);
        Assert.assertTrue(sgsHome.exists());
        Assert.assertTrue(sgsHome.isDirectory());
        Assert.assertTrue(new File(sgsHome, "README").exists());
    }

    @Test(expected=MojoExecutionException.class)
    public void testExecuteNoSgsHome() throws Exception {
        InstallMojo mojo = lookupDummyMojo("target/test-classes/unit/install/config.xml");
        Assert.assertNotNull(mojo);

        fillDefaultValues(mojo);
        fillRepositories(mojo);
        this.setVariableValueToObject(mojo, "sgsHome", null);
        mojo.execute();
    }

    @Test(expected=MojoExecutionException.class)
    public void testExecuteInvalidServer() throws Exception {
        InstallMojo mojo = lookupDummyMojo("target/test-classes/unit/install/config.xml");
        Assert.assertNotNull(mojo);

        fillDefaultValues(mojo);
        fillRepositories(mojo);
        this.setVariableValueToObject(mojo, "artifactId", "does-not-exist");
        mojo.execute();
    }

    @Test
    public void testExecuteCleanSgsHome() throws Exception {
        InstallMojo mojo = lookupDummyMojo("target/test-classes/unit/install/config-home.xml");
        Assert.assertNotNull(mojo);

        fillDefaultValues(mojo);
        fillRepositories(mojo);
        File sgsHome = (File) this.getVariableValueFromObject(mojo, "sgsHome");
        if (sgsHome.exists() && sgsHome.isDirectory()) {
            FileUtils.deleteDirectory(sgsHome);
        }
        FileUtils.mkdir(sgsHome.getAbsolutePath());
        this.setVariableValueToObject(mojo, "cleanSgsHome", true);
        mojo.execute();

        Assert.assertTrue(sgsHome.exists());
        Assert.assertTrue(sgsHome.isDirectory());
        Assert.assertTrue(new File(sgsHome, "README").exists());
    }

    @Test
    public void testExecuteDontCleanSgsHome() throws Exception {
        InstallMojo mojo = lookupDummyMojo("target/test-classes/unit/install/config-home.xml");
        Assert.assertNotNull(mojo);

        fillDefaultValues(mojo);
        fillRepositories(mojo);
        File sgsHome = (File) this.getVariableValueFromObject(mojo, "sgsHome");
        if (sgsHome.exists() && sgsHome.isDirectory()) {
            FileUtils.deleteDirectory(sgsHome);
        }
        FileUtils.mkdir(sgsHome.getAbsolutePath());
        this.setVariableValueToObject(mojo, "cleanSgsHome", false);
        mojo.execute();

        Assert.assertTrue(sgsHome.exists());
        Assert.assertTrue(sgsHome.isDirectory());
        Assert.assertFalse(new File(sgsHome, "README").exists());
    }

    @Test(expected=MojoExecutionException.class)
    public void testExecuteInvalidOutputDirectory() throws Exception {
        InstallMojo mojo = lookupDummyMojo("target/test-classes/unit/install/config-home.xml");
        Assert.assertNotNull(mojo);

        fillDefaultValues(mojo);
        fillRepositories(mojo);
        File sgsHome = (File) this.getVariableValueFromObject(mojo, "sgsHome");
        if (sgsHome.exists() && sgsHome.isDirectory()) {
            FileUtils.deleteDirectory(sgsHome);
        }

        this.setVariableValueToObject(mojo, "outputDirectory", new File("does-not-exist"));
        mojo.execute();
    }

}
