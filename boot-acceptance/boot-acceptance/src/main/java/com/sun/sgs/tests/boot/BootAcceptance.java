/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.tests.boot;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.zip.ZipFile;
import java.io.File;
import java.net.URL;

import org.junit.runner.JUnitCore;
import org.junit.Before;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class BootAcceptance {
    
    private static final Logger logger =Logger.getLogger(
            BootAcceptance.class.getName());
    private static final String TEST_DIR = "bootAcceptance";
    private static final String ALTERNATE_DIR = "bootAlternate";
    private static final String SGS_BOOT = "alt-boot.properties";
    private static final String SGS_DEPLOY = "alt-deploy";
    private static final String SGS_PROPERTIES = "alt-server.properties";
    private static final String SGS_LOGGING = "alt-logging.properties";
    private static final String SGS_LOGFILE = "alt.log";
    private static File distribution;
    
    private File testDirectory;
    private File installationDirectory;
    private Process server;
    private Process stopper;
    private String config;
    
    private File alternateDirectory;
    private File alternateSGS_BOOT;
    private File alternateSGS_DEPLOY;
    private File alternateSGS_PROPERTIES;
    private File alternateSGS_LOGGING;
    private File alternateSGS_LOGFILE;

    /**
     * Main-line method that initiates the suite of tests against the given
     * Project Darkstar distribution.
     * 
     * @param args a filename of the Project Darkstar zip distribution to test
     */
    public static void main(String[] args) {
        if(args.length != 1) {
            logger.log(Level.SEVERE, "Invalid number of arguments:");
            throw new RuntimeException("Invalid number of arguments");
        }
        BootAcceptance.distribution = new File(args[0]);
        
        new JUnitCore().main(BootAcceptance.class.getName());
    }
    
    /**
     * Create the temporary test directory and installs the distribution
     * into it.
     */
    @Before
    public void setupTestDirectory() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir");
        if(!tempDir.endsWith(File.separator)) {
            tempDir += File.separator;
        }

        //setup test directory
        File d  = new File(tempDir + BootAcceptance.TEST_DIR);
        if(d.exists()) {
            Assert.fail("Unable to create test directory " + 
                        "that already exists : " + d);
        }
        Assert.assertTrue(d.mkdirs());
        this.testDirectory = d;
        
        //unzip installation into test directory
        Util.unzip(new ZipFile(distribution), testDirectory);
        File[] files = testDirectory.listFiles();
        Assert.assertEquals(files.length, 1);
        this.installationDirectory = files[0];
        Assert.assertTrue(installationDirectory.isDirectory());

        //setup alternate directory
        File a = new File(installationDirectory, BootAcceptance.ALTERNATE_DIR);
        if(a.exists()) {
            Assert.fail("Unable to create alternate home directory " + 
                        "that already exists : " + a);
        }
        
        Assert.assertTrue(a.mkdirs());
        this.alternateDirectory = a;
        
        alternateSGS_DEPLOY = new File(alternateDirectory, 
                                       BootAcceptance.SGS_DEPLOY);
        Assert.assertTrue(alternateSGS_DEPLOY.mkdirs());

        this.alternateSGS_BOOT = new File(alternateDirectory,
                                          BootAcceptance.SGS_BOOT);
        Assert.assertFalse(alternateSGS_BOOT.exists());
        
        //copy sgs-server.properties to alternate location
        File actualSGS_PROPERTIES = new File(installationDirectory,
                                             "conf/sgs-server.properties");
        this.alternateSGS_PROPERTIES = new File(alternateDirectory,
                                                BootAcceptance.SGS_PROPERTIES);
        Util.copyFileToFile(actualSGS_PROPERTIES, alternateSGS_PROPERTIES);
        
        //copy sgs-logging.properties to alternate location
        File actualSGS_LOGGING = new File(installationDirectory,
                                          "conf/sgs-logging.properties");
        this.alternateSGS_LOGGING = new File(alternateDirectory,
                                             BootAcceptance.SGS_LOGGING);
        Util.copyFileToFile(actualSGS_LOGGING, alternateSGS_LOGGING);
        
        //specify location of logfile
        this.alternateSGS_LOGFILE = new File(alternateDirectory,
                                             BootAcceptance.SGS_LOGFILE);
        
    }
    
    
    
    /**
     * Destroy the temporary test directory
     */
    @After
    public void removeTestDirectory() throws Exception {
        if(server != null) {
            Util.destroyProcess(stopper);
            stopper = Util.shutdownPDS(installationDirectory, config);
        }
        Util.destroyProcess(stopper);
        Util.destroyProcess(server);
        this.config = null;
        this.server = null;
        this.stopper = null;
        Assert.assertTrue(Util.deleteDirectory(testDirectory));
        Assert.assertTrue(Util.deleteDirectory(alternateDirectory));
    }
    
    @Test(timeout=5000)
    public void testEmptyDeploy() throws Exception {
        this.config = "";
        this.server = Util.bootPDS(installationDirectory, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "WARNING: No application jar found with a" +
                                 " META-INF/app.properties configuration file",
                                 "SEVERE: Missing required property" +
                                 " com.sun.sgs.app.name"));
        
        //ensure that the process has exited
        try {
            //give the process time to complete
            Thread.sleep(500);
            this.server.exitValue();
            Util.destroyProcess(server);
        } catch (IllegalThreadStateException e) {
            Assert.fail("Server process has not exited but should have");
        }
    }
    
    @Test(timeout=5000)
    public void testHelloWorldDefault() throws Exception {
        Util.loadTutorial(installationDirectory);
        this.config = "";
        this.server = Util.bootPDS(installationDirectory, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    @Test(timeout=5000)
    public void testCustomSGS_DEPLOY() throws Exception {
        Util.loadTutorial(installationDirectory, alternateSGS_DEPLOY);
        URL bootConfig = this.getClass().getResource(
                "customSGS_DEPLOY.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    @Test(timeout=5000)
    public void testCustomSGS_PROPERTIES() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "customSGS_PROPERTIES.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        Util.clearSGS_PROPERTIES(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    @Test(timeout=5000)
    public void testCustomSGS_LOGGING() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "customSGS_LOGGING.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        Util.clearSGS_LOGGING(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    @Test(timeout=5000)
    public void testCustomALL_CONF() throws Exception {
        Util.loadTutorial(installationDirectory, alternateSGS_DEPLOY);
        URL bootConfig = this.getClass().getResource(
                "customALL_CONF.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyFile(bootConfig, alternateSGS_BOOT);
        Util.clearALL_CONF(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    @Test(timeout=5000)
    public void testCustomSGS_LOGFILE() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "customSGS_LOGFILE.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, config);
        //give server time to boot
        Thread.sleep(2000);
        Assert.assertTrue(this.alternateSGS_LOGFILE.exists());
        Assert.assertTrue(Util.expectLines(this.alternateSGS_LOGFILE,
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
}
