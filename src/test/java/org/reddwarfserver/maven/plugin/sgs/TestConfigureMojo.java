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

import org.codehaus.plexus.util.FileUtils;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.junit.Assert;
import org.junit.Before;
import org.junit.After;

import java.io.File;

/**
 * Test the {@code ConfigureMojo} class
 */
@RunWith(JUnit4.class)
public class TestConfigureMojo extends AbstractTestSgsMojo {

    private static final String POM = "target/test-classes/unit/configure/config.xml";
    private File outputDirectory;

    private AbstractSgsMojo mojo;
    private File sgsHome;
    private File dummyFile;
    private File sgsBoot;
    private File sgsServer;
    private File sgsLogging;
    
    protected AbstractSgsMojo buildEmptyMojo() {
        return new ConfigureMojo();
    }
    
    @Before
    public void buildDummyHomeMojo() throws Exception {
        mojo = this.lookupDummyMojo(ConfigureMojo.class, "configure", POM);
        sgsHome = (File) this.getVariableValueFromObject(mojo, "sgsHome");
        dummyFile = new File(getBasedir(),
                             "target" + File.separator +
                             "test-classes" + File.separator +
                             "unit" + File.separator +
                             "configure" + File.separator +
                             "dummy.properties");
        sgsBoot = new File(sgsHome,
                           "conf" + File.separator +
                           ConfigureMojo.SGS_BOOT);
        sgsServer = new File(sgsHome,
                             "conf" + File.separator +
                             ConfigureMojo.SGS_SERVER);
        sgsLogging = new File(sgsHome,
                              "conf" + File.separator +
                              ConfigureMojo.SGS_LOGGING);

        outputDirectory = new File(getBasedir(),
                             "target" + File.separator +
                             "test-classes" + File.separator +
                             "unit" + File.separator +
                             "configure");
    }
    
    @After
    public void scrubHome() throws Exception {
        if (sgsHome.exists()) {
            FileUtils.deleteDirectory(sgsHome);
        }
    }
    
    @Test
    public void testExecuteSgsBootConfig() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "sgsBoot", dummyFile);
        mojo.execute();
        
        Assert.assertTrue(sgsBoot.exists());
        Assert.assertTrue(sgsServer.exists());
        Assert.assertTrue(sgsLogging.exists());
        
        Assert.assertEquals(FileUtils.fileRead(sgsBoot),
                            FileUtils.fileRead(dummyFile));
        Assert.assertNotSame(FileUtils.fileRead(sgsServer),
                             FileUtils.fileRead(dummyFile));
        Assert.assertNotSame(FileUtils.fileRead(sgsLogging),
                             FileUtils.fileRead(dummyFile));
    }
    
    @Test
    public void testExecuteSgsServerConfig() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "sgsServer", dummyFile);
        mojo.execute();
        
        Assert.assertTrue(sgsBoot.exists());
        Assert.assertTrue(sgsServer.exists());
        Assert.assertTrue(sgsLogging.exists());

        Assert.assertNotSame(FileUtils.fileRead(sgsBoot),
                             FileUtils.fileRead(dummyFile));
        Assert.assertEquals(FileUtils.fileRead(sgsServer),
                            FileUtils.fileRead(dummyFile));
        Assert.assertNotSame(FileUtils.fileRead(sgsLogging),
                             FileUtils.fileRead(dummyFile));
    }
    
    @Test
    public void testExecuteSgsLoggingConfig() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "sgsLogging", dummyFile);
        mojo.execute();
        
        Assert.assertTrue(sgsBoot.exists());
        Assert.assertTrue(sgsServer.exists());
        Assert.assertTrue(sgsLogging.exists());

        Assert.assertNotSame(FileUtils.fileRead(sgsBoot),
                             FileUtils.fileRead(dummyFile));
        Assert.assertNotSame(FileUtils.fileRead(sgsServer),
                             FileUtils.fileRead(dummyFile));
        Assert.assertEquals(FileUtils.fileRead(sgsLogging),
                            FileUtils.fileRead(dummyFile));
    }
    
    @Test
    public void testExecuteAllConfig() throws Exception{
        this.executeInstall(POM, outputDirectory);
        this.setVariableValueToObject(mojo, "sgsBoot", dummyFile);
        this.setVariableValueToObject(mojo, "sgsServer", dummyFile);
        this.setVariableValueToObject(mojo, "sgsLogging", dummyFile);
        mojo.execute();
        
        Assert.assertTrue(sgsBoot.exists());
        Assert.assertTrue(sgsServer.exists());
        Assert.assertTrue(sgsLogging.exists());
        
        Assert.assertEquals(FileUtils.fileRead(sgsBoot),
                            FileUtils.fileRead(dummyFile));
        Assert.assertEquals(FileUtils.fileRead(sgsServer),
                            FileUtils.fileRead(dummyFile));
        Assert.assertEquals(FileUtils.fileRead(sgsLogging),
                            FileUtils.fileRead(dummyFile));
    }
    
    
}
