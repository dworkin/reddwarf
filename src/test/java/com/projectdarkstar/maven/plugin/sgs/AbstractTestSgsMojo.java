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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

/**
 * Common tests for sgs mojos.
 */
public abstract class AbstractTestSgsMojo extends AbstractMojoTestCase {

    // support install for all subclass tests
    private String groupId = "com.projectdarkstar.server";
    private String artifactId = "sgs-server-dist";
    private String type = "zip";
    private String version = "0.9.11";
    private boolean cleanSgsHome = true;

    protected void fillDefaultValues(InstallMojo mojo) throws Exception {
        this.setVariableValueToObject(mojo, "groupId", groupId);
        this.setVariableValueToObject(mojo, "artifactId", artifactId);
        this.setVariableValueToObject(mojo, "type", type);
        this.setVariableValueToObject(mojo, "version", version);
        this.setVariableValueToObject(mojo, "cleanSgsHome", cleanSgsHome);
    }

    protected void fillRepositories(AbstractSgsMojo mojo) throws Exception {

        ArtifactRepository localRepository = new DefaultArtifactRepository(
                "local", "file://" + getBasedir() + "/target/local-repo",
                new DefaultRepositoryLayout());
        this.setVariableValueToObject(mojo, "localRepository", localRepository);

        ArtifactRepository remoteRepository = new DefaultArtifactRepository(
                "java.net",
                "http://download.java.net/maven/2",
                new DefaultRepositoryLayout());
        this.setVariableValueToObject(
                mojo, "remoteRepositories",
                Collections.singletonList(remoteRepository));

    }

    protected void executeInstall(String pom,
                                  File outputDirectory) throws Exception {
        InstallMojo mojo = lookupDummyMojo(InstallMojo.class, "install", pom);
        fillDefaultValues(mojo);
        fillRepositories(mojo);
        this.setVariableValueToObject(mojo, "outputDirectory", outputDirectory);
        mojo.execute();
    }

    protected <T> T lookupDummyMojo(Class<T> mojoClass, String goal, String pom)
            throws Exception {
        File testPom = new File( getBasedir(), pom);
        return mojoClass.cast(lookupMojo(goal, testPom));
    }
    
    protected abstract AbstractSgsMojo buildEmptyMojo() throws Exception;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckConfigBlank() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        this.setVariableValueToObject(mojo, "sgsHome", null);
        mojo.checkConfig();
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckConfigNotExists() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        this.setVariableValueToObject(mojo, "sgsHome", new File("/no/such/file"));
        mojo.checkConfig();
    }
    
    @Test
    public void testCheckConfigOk() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        File dummyHome = new File(getBasedir(),
                                  "target" + File.separator +
                                  "test-classes" + File.separator +
                                  "unit" + File.separator +
                                  "abstract");
        this.setVariableValueToObject(mojo, "sgsHome", dummyHome);
        mojo.checkConfig();
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckDirectoryNull() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        mojo.checkDirectory(null);
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckDirectoryNotExists() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        mojo.checkDirectory(new File("/no/such/file"));
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckDirectoryNotDirectory() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        File dummyFile = new File(getBasedir(),
                                  "target" + File.separator +
                                  "test-classes" + File.separator +
                                  "unit" + File.separator +
                                  "abstract" + File.separator +
                                  "dummy.properties");
        mojo.checkDirectory(dummyFile);
    }
    
    @Test
    public void testCheckDirectoryOk() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        File dummyHome = new File(getBasedir(),
                                  "target" + File.separator +
                                  "test-classes" + File.separator +
                                  "unit" + File.separator +
                                  "abstract");
        mojo.checkDirectory(dummyHome);
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckFileNull() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        mojo.checkFile(null);
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckFileNotExists() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        mojo.checkFile(new File("/no/such/file"));
    }
    
    @Test(expected=MojoExecutionException.class)
    public void testCheckFileNotFile() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        File dummyHome = new File(getBasedir(),
                                  "target" + File.separator +
                                  "test-classes" + File.separator +
                                  "unit" + File.separator +
                                  "abstract");
        mojo.checkFile(dummyHome);
    }
    
    @Test
    public void testCheckFileOk() throws Exception {
        AbstractSgsMojo mojo = buildEmptyMojo();
        File dummyFile = new File(getBasedir(),
                                  "target" + File.separator +
                                  "test-classes" + File.separator +
                                  "unit" + File.separator +
                                  "abstract" + File.separator +
                                  "dummy.properties");
        mojo.checkFile(dummyFile);
    }

    
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
