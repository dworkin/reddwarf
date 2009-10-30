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
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.junit.Assert;
import org.junit.Before;
import org.junit.After;
import java.io.File;

/**
 * Test the {@code DeployMojo} class
 */
@RunWith(JUnit4.class)
public class TestDeployMojo extends AbstractTestSgsMojo {

    private static final String POM = "target/test-classes/unit/deploy/config.xml";
    private File outputDirectory;

    private AbstractSgsMojo mojo;
    private File sgsHome;
    private File deployDir;
    private File dummyFile1;
    private File dummyFile2;
    private File dummyZip;
    private File noFile;

    protected AbstractSgsMojo buildEmptyMojo() {
        return new DeployMojo();
    }

    @Before
    public void buildDummyHomeMojo() throws Exception {
        mojo = this.lookupDummyMojo(DeployMojo.class, "deploy", POM);
        sgsHome = (File) this.getVariableValueFromObject(mojo, "sgsHome");
        deployDir = new File(sgsHome, "deploy");

        dummyFile1 = new File(getBasedir(),
                              "target" + File.separator +
                              "test-classes" + File.separator +
                              "unit" + File.separator +
                              "deploy" + File.separator +
                              "dummy.file");
        dummyFile2 = new File(getBasedir(),
                              "target" + File.separator +
                              "test-classes" + File.separator +
                              "unit" + File.separator +
                              "deploy" + File.separator +
                              "dummy2.file");
        dummyZip = new File(getBasedir(),
                            "target" + File.separator +
                            "test-classes" + File.separator +
                            "unit" + File.separator +
                            "deploy" + File.separator +
                            "dummy.zip");
        noFile = new File("does-not-exist");

        outputDirectory = new File(getBasedir(),
                                   "target" + File.separator +
                                   "test-classes" + File.separator +
                                   "unit" + File.separator +
                                   "deploy");
    }

    @After
    public void scrubHome() throws Exception {
        if (sgsHome.exists()) {
            FileUtils.deleteDirectory(sgsHome);
        }
    }

    @Test
    public void testExecuteValidSingleFile() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "files", new File[]{dummyFile1});
        mojo.execute();

        Assert.assertTrue(deployDir.exists());
        Assert.assertTrue(deployDir.isDirectory());
        Assert.assertEquals(1, deployDir.listFiles().length);

        Assert.assertEquals(FileUtils.fileRead(deployDir.listFiles()[0]),
                            FileUtils.fileRead(dummyFile1));
    }

    @Test
    public void testExecuteValidMultipleFiles() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "files", new File[]{dummyFile1, dummyFile2});
        mojo.execute();

        Assert.assertTrue(deployDir.exists());
        Assert.assertTrue(deployDir.isDirectory());
        Assert.assertEquals(2, deployDir.listFiles().length);

        // verify that both files have been deployed
        Assert.assertTrue(FileUtils.fileRead(deployDir.listFiles()[0]).equals(FileUtils.fileRead(dummyFile1)) ||
                          FileUtils.fileRead(deployDir.listFiles()[0]).equals(FileUtils.fileRead(dummyFile2)));
        Assert.assertTrue(FileUtils.fileRead(deployDir.listFiles()[1]).equals(FileUtils.fileRead(dummyFile1)) ||
                          FileUtils.fileRead(deployDir.listFiles()[1]).equals(FileUtils.fileRead(dummyFile2)));
    }

    @Test(expected=MojoExecutionException.class)
    public void testExecuteInvalidFile() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "files", new File[]{noFile});
        mojo.execute();
    }

    @Test(expected=MojoExecutionException.class)
    public void testExecuteInvalidDeployDir() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "deployDir", noFile);
        mojo.execute();
    }

    @Test
    public void testExecuteValidZipFileUnpack() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "files", new File[]{dummyZip});
        this.setVariableValueToObject(mojo, "unpack", true);
        mojo.execute();

        Assert.assertTrue(deployDir.exists());
        Assert.assertTrue(deployDir.isDirectory());
        Assert.assertEquals(2, deployDir.listFiles().length);

        // verify that the zip file has been unpacked
        Assert.assertTrue(FileUtils.fileRead(deployDir.listFiles()[0]).equals(FileUtils.fileRead(dummyFile1)) ||
                          FileUtils.fileRead(deployDir.listFiles()[0]).equals(FileUtils.fileRead(dummyFile2)));
        Assert.assertTrue(FileUtils.fileRead(deployDir.listFiles()[1]).equals(FileUtils.fileRead(dummyFile1)) ||
                          FileUtils.fileRead(deployDir.listFiles()[1]).equals(FileUtils.fileRead(dummyFile2)));
    }

    @Test(expected=MojoExecutionException.class)
    public void testExecuteInvalidZipFileUnpack() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "files", new File[]{dummyFile1});
        this.setVariableValueToObject(mojo, "unpack", true);
        mojo.execute();
    }
}

