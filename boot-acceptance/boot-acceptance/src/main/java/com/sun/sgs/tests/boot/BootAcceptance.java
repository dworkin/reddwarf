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
    private static final String CONFIG_LOGGING = "config.logging";
    private static final int TIMEOUT = 10000;
    private static final int SMALL_PAUSE = 1000;
    private static final int LARGE_PAUSE = 5000;
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
    private File configLogging;
    
    private File homeConfig;
    private boolean removeHomeConfig;

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
        
        //copy config logging file
        this.configLogging = new File(alternateDirectory,
                                      BootAcceptance.CONFIG_LOGGING);
        URL configConfig = this.getClass().getResource(
                "config-logging.properties");
        Assert.assertNotNull(configConfig);
        Util.copyURLToFile(configConfig, configLogging);
        
        this.homeConfig = new File(System.getProperty("user.home") +                            
                                   File.separator +
                                   ".sgs.properties");
        this.removeHomeConfig = false;
    }
    
    
    
    /**
     * Destroy the temporary test directory
     */
    @After
    public void removeTestDirectory() throws Exception {
        if(server != null) {
            Util.destroyProcess(stopper);
            stopper = Util.shutdownPDS(installationDirectory, config);
            stopper.waitFor();
        }
        Util.destroyProcess(stopper);
        Util.destroyProcess(server);
        this.config = null;
        this.server = null;
        this.stopper = null;
        Assert.assertTrue(Util.deleteDirectory(testDirectory));
        Assert.assertTrue(Util.deleteDirectory(alternateDirectory));
        
        if(this.removeHomeConfig) {
            this.homeConfig.delete();
        }
    }
    
    /**
     * Verify proper behavior with an empty deploy directory
     */
    @Test(timeout=TIMEOUT)
    public void testEmptyDeploy() throws Exception {
        this.config = "";
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "WARNING: No application jar found with a" +
                                 " META-INF/app.properties configuration file",
                                 "SEVERE: Missing required property" +
                                 " com.sun.sgs.app.name"));
        
        //ensure that the process has exited
        try {
            //give the process time to complete
            Thread.sleep(SMALL_PAUSE);
            this.server.exitValue();
            Util.destroyProcess(server);
        } catch (IllegalThreadStateException e) {
            Assert.fail("Server process has not exited but should have");
        }
    }
    
    /**
     * Default tutorial copied into the deploy directory
     */
    @Test(timeout=TIMEOUT)
    public void testHelloWorldDefault() throws Exception {
        Util.loadTutorial(installationDirectory);
        this.config = "";
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    /**
     * Test using a custom sgs-boot properties with custom SGS_DEPLOY directory
     */
    @Test(timeout=TIMEOUT)
    public void testCustomSGS_DEPLOY() throws Exception {
        Util.loadTutorial(installationDirectory, alternateSGS_DEPLOY);
        URL bootConfig = this.getClass().getResource(
                "customSGS_DEPLOY.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(Util.expectLines(server,
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    /**
     * Test using custom sgs-boot properties with custom SGS_PROEPERTIES file
     */
    @Test(timeout=TIMEOUT)
    public void testCustomSGS_PROPERTIES() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "customSGS_PROPERTIES.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        Util.clearSGS_PROPERTIES(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    /**
     * Test using custom sgs-boot properties with custom SGS_LOGGING file
     */
    @Test(timeout=TIMEOUT)
    public void testCustomSGS_LOGGING() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "customSGS_LOGGING.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        Util.clearSGS_LOGGING(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    /**
     * Test using custom sgs-boot properties with a custom
     * SGS_DEPLOY directory, SGS_PROPERTIES file, and SGS_LOGGING file
     */
    @Test(timeout=TIMEOUT)
    public void testCustomALL_CONF() throws Exception {
        Util.loadTutorial(installationDirectory, alternateSGS_DEPLOY);
        URL bootConfig = this.getClass().getResource(
                "customALL_CONF.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearALL_CONF(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(Util.expectLines(server, 
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    /**
     * Test with a configured SGS_LOGFILE to redirect output
     */
    @Test(timeout=TIMEOUT)
    public void testCustomSGS_LOGFILE() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "customSGS_LOGFILE.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        //give server time to boot
        Thread.sleep(LARGE_PAUSE);
        Assert.assertTrue(this.alternateSGS_LOGFILE.exists());
        Assert.assertTrue(Util.expectLines(this.alternateSGS_LOGFILE,
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    /**
     * Verify correct behavior with default BDB_TYPE (none specified)
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPEDefault() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPEDefault.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder exp = new StringBuilder();
        exp.append("-cp .*lib.db-.*\\.jar");
        exp.append(".*");
        exp.append("\\-Djava\\.library\\.path=.*natives");
        exp.append(".*");
        exp.append("\\Q-Dcom.sun.sgs.impl.service.data.store.db.environment.class=");
        exp.append("com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment\\E");
        Assert.assertTrue(Util.expectMatches(server,
                                             exp.toString(),
                                             "The Kernel is ready",
                                             "HelloWorld: application is ready"));
    }
    
    /**
     * Verify default BDB_TYPE does NOT include the BDB-JE jar file on
     * the classpath
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPEDefaultNoJe() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPEDefault.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //verify the je jar is not included in the classpath
        Assert.assertTrue(Util.expectMatchNoMatch(server,
                                                  "-cp .*lib.db-.*\\.jar",
                                                  "-cp .*lib.je-.*\\.jar"));
    }
    
    /**
     * Verify correct behavior with BDB_TYPE set to db
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPEDb() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPEDb.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder exp = new StringBuilder();
        exp.append("-cp .*lib.db-.*\\.jar");
        exp.append(".*");
        exp.append("\\-Djava\\.library\\.path=.*natives");
        exp.append(".*");
        exp.append("\\Q-Dcom.sun.sgs.impl.service.data.store.db.environment.class=");
        exp.append("com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment\\E");
        Assert.assertTrue(Util.expectMatches(server,
                                             exp.toString(),
                                             "The Kernel is ready",
                                             "HelloWorld: application is ready"));
    }
    
    /**
     * Verify db BDB_TYPE does NOT include the BDB-JE jar file on
     * the classpath
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPEDbNoJe() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPEDb.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //verify the je jar is not included in the classpath
        Assert.assertTrue(Util.expectMatchNoMatch(server,
                                                  "-cp .*lib.db-.*\\.jar",
                                                  "-cp .*lib.je-.*\\.jar"));
    }
    
    /**
     * Verify correct behavior with BDB_TYPE set to je
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPEJe() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPEJe.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder exp = new StringBuilder();
        exp.append("-cp .*lib.je-.*\\.jar");
        exp.append(".*");
        exp.append("\\-Djava\\.library\\.path= ");
        exp.append(".*");
        exp.append("\\Q-Dcom.sun.sgs.impl.service.data.store.db.environment.class=");
        exp.append("com.sun.sgs.impl.service.data.store.db.je.JeEnvironment\\E");
        Assert.assertTrue(Util.expectMatches(server,
                                             exp.toString(),
                                             "The Kernel is ready",
                                             "HelloWorld: application is ready"));
    }
    
    /**
     * Verify je BDB_TYPE does NOT include the BDB jar file on
     * the classpath
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPEJeNoDb() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPEJe.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //verify the db jar is not included in the classpath
        Assert.assertTrue(Util.expectMatchNoMatch(server,
                                                  "-cp .*lib.je-.*\\.jar",
                                                  "-cp .*lib.db-.*\\.jar"));
    }
    
    /**
     * Verify correct behavior with BDB_TYPE set to custom
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPECustom() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPECustom.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder match = new StringBuilder();
        match.append("-cp .*custom.jar");
        match.append(".*");
        match.append("\\-Djava\\.library\\.path= ");
        StringBuilder noMatch = new StringBuilder();
        noMatch.append("\\Q-Dcom.sun.sgs.impl.service.data.store.db.environment.class\\E");
        Assert.assertTrue(Util.expectMatchNoMatch(server,
                                                  match.toString(),
                                                  noMatch.toString()));
    }
    
    /**
     * Verify custom BDB_TYPE does NOT include the BDB jar file on
     * the classpath
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPECustomNoDb() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPECustom.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder match = new StringBuilder();
        match.append("-cp .*custom.jar");
        match.append(".*");
        match.append("\\-Djava\\.library\\.path= ");
        StringBuilder noMatch = new StringBuilder();
        noMatch.append("-cp .*lib.db-.*\\.jar");
        Assert.assertTrue(Util.expectMatchNoMatch(server,
                                                  match.toString(),
                                                  noMatch.toString()));
    }
    
    /**
     * Verify custom BDB_TYPE does NOT include the BDB-JE jar file on
     * the classpath
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_TYPECustomNoJe() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_TYPECustom.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder match = new StringBuilder();
        match.append("-cp .*custom.jar");
        match.append(".*");
        match.append("\\-Djava\\.library\\.path= ");
        StringBuilder noMatch = new StringBuilder();
        noMatch.append("-cp .*lib.je-.*\\.jar");
        Assert.assertTrue(Util.expectMatchNoMatch(server,
                                                  match.toString(),
                                                  noMatch.toString()));
    }
    
    /**
     * Verify CUSTOM_NATIVES are property included in the java.library.path
     * when the BDB_TYPE is set to db
     */
    @Test(timeout=TIMEOUT)
    public void testCUSTOM_NATIVESDb() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "CUSTOM_NATIVESDb.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder match = new StringBuilder();
        match.append("-cp .*lib.db-.*\\.jar");
        match.append(".*");
        match.append("\\-Djava\\.library\\.path=.*natives.*customDirectory ");
        Assert.assertTrue(Util.expectMatches(server,
                                             match.toString(),
                                             "The Kernel is ready",
                                             "HelloWorld: application is ready"));
    }
    
    /**
     * Verify CUSTOM_NATIVES are property included in the java.library.path
     * when the BDB_TYPE is set to je
     */
    @Test(timeout=TIMEOUT)
    public void testCUSTOM_NATIVESJe() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "CUSTOM_NATIVESJe.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder match = new StringBuilder();
        match.append("-cp .*lib.je-.*\\.jar");
        match.append(".*");
        match.append("\\-Djava\\.library\\.path=customDirectory ");
        Assert.assertTrue(Util.expectMatches(server,
                                             match.toString(),
                                             "The Kernel is ready",
                                             "HelloWorld: application is ready"));
    }
    
    /**
     * Verify CUSTOM_NATIVES are property included in the java.library.path
     * when the BDB_TYPE is set to custom
     */
    @Test(timeout=TIMEOUT)
    public void testCUSTOM_NATIVESCustom() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "CUSTOM_NATIVESCustom.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder match = new StringBuilder();
        match.append("\\-Djava\\.library\\.path=customDirectory ");
        Assert.assertTrue(Util.expectMatches(server,
                                             match.toString()));
    }
    
    /**
     * Verify a custom set of specified BDB_NATIVES are included in the
     * java.library.path properly
     */
    @Test(timeout=TIMEOUT)
    public void testBDB_NATIVES() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "BDB_NATIVES.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        //match the regular expression to verify components of the 
        //execute path
        StringBuilder match = new StringBuilder();
        match.append("-cp .*lib.db-.*\\.jar");
        match.append(".*");
        match.append("\\-Djava\\.library\\.path=bdbDirectory ");
        Assert.assertTrue(Util.expectMatches(server,
                                             match.toString()));
    }
    
    /**
     * Verify that custom JAVA_OPTS are included on the executed process
     */
    @Test(timeout=TIMEOUT)
    public void testCustomJAVA_OPTS() throws Exception {
        Util.loadTutorial(installationDirectory);
        URL bootConfig = this.getClass().getResource(
                "customJAVA_OPTS.properties");
        Assert.assertNotNull(bootConfig);
        Util.copyURLToFile(bootConfig, alternateSGS_BOOT);
        Util.clearSGS_BOOT(installationDirectory);
        
        this.config = alternateSGS_BOOT.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, 
                                   configLogging, config);
        
        Assert.assertTrue(Util.expectLines(server,
                                           "-server -DmyProperty=test -Xmx768M",
                                           "The Kernel is ready",
                                           "HelloWorld: application is ready"));
    }
    
    /**
     * Verify that a ~/.sgs.properties file is used when it exists
     */
    @Test(timeout=TIMEOUT)
    public void testPropertiesHomeDirectory() throws Exception {
        if(this.homeConfig.exists()) {
            Assert.fail("Can't run test, file already exists : " +
                        this.homeConfig.getAbsolutePath());
        }
        
        //copy the ~/.sgs.properties file into position
        this.removeHomeConfig = true;
        Util.loadTutorial(installationDirectory);
        URL sgsConfig = this.getClass().getResource(
                "propertiesHome.sgs.properties");
        Assert.assertNotNull(sgsConfig);
        Util.copyURLToFile(sgsConfig, this.homeConfig);
        
        this.config = "";
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloLogger: application is ready"));
    }
    
    /**
     * Verify that properties in the ~/.sgs.properties file override
     * properties in the SGS_PROPERTIES file
     */
    @Test(timeout=TIMEOUT)
    public void testPropertiesHomeOverrides() throws Exception {
        if(this.homeConfig.exists()) {
            Assert.fail("Can't run test, file already exists : " +
                        this.homeConfig.getAbsolutePath());
        }
        
        //copy the ~/.sgs.properties file into position
        this.removeHomeConfig = true;
        Util.loadTutorial(installationDirectory);
        URL sgsConfig = this.getClass().getResource(
                "propertiesHome.sgs.properties");
        Assert.assertNotNull(sgsConfig);
        Util.copyURLToFile(sgsConfig, this.homeConfig);
        
        //copy the sgs-server.properties file into position
        File boot = new File(this.alternateDirectory,
                             ".sgs-server.properties.boot");
        URL sgsServerConfig = this.getClass().getResource(
                "propertiesHome.sgs-server.properties.boot");
        Assert.assertNotNull(sgsServerConfig);
        Util.copyURLToFile(sgsServerConfig, boot);
        
        //copy the SGS_PROPERTIES file referenced into position
        URL sgsServerActConfig = this.getClass().getResource(
                "propertiesHome.sgs-server.properties");
        Assert.assertNotNull(sgsServerActConfig);
        Util.copyURLToFile(sgsServerActConfig,
                           new File(this.alternateDirectory, 
                                    ".sgs-server.properties"));
        
        Util.clearSGS_BOOT(installationDirectory);
        Util.clearSGS_PROPERTIES(installationDirectory);
        
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloLogger: application is ready"));
    }
    
    /**
     * Verify that properties given on the command line override those
     * given in ~/.sgs.properties file
     */
    @Test(timeout=TIMEOUT)
    public void testPropertiesCommandOverrides() throws Exception {
        if(this.homeConfig.exists()) {
            Assert.fail("Can't run test, file already exists : " +
                        this.homeConfig.getAbsolutePath());
        }
        
        //copy the ~/.sgs.properties file into position
        this.removeHomeConfig = true;
        Util.loadTutorial(installationDirectory);
        URL sgsConfig = this.getClass().getResource(
                "propertiesHome.sgs.properties");
        Assert.assertNotNull(sgsConfig);
        Util.copyURLToFile(sgsConfig, this.homeConfig);
        
        //copy the boot file into position which contains overriding JAVA_OPTS
        File boot = new File(this.alternateDirectory,
                             ".sgs-server.properties.boot");
        URL sgsServerConfig = this.getClass().getResource(
                "propertiesCommand.sgs-server.properties.boot");
        Assert.assertNotNull(sgsServerConfig);
        Util.copyURLToFile(sgsServerConfig, boot);
        
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloEcho: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloChannels() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloChannels.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloChannels: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloEcho() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloEcho.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloEcho: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloLogger() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloLogger.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloLogger: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloPersistence() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloPersistence.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloPersistence: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloPersistence2() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloPersistence2.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloPersistence2: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloPersistence3() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloPersistence3.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloPersistence3: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloTimer() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloTimer.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloTimer: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloUser() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloUser.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloUser: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloUser2() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloUser2.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloUser2: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialHelloWorld() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/HelloWorld.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "HelloWorld: application is ready"));
    }
    
    @Test(timeout=TIMEOUT)
    public void testTutorialSwordWorld() throws Exception {
        Util.loadTutorial(installationDirectory);
        File boot = new File(installationDirectory, 
                             "tutorial/conf/SwordWorld.boot");
        this.config = boot.getAbsolutePath();
        this.server = Util.bootPDS(installationDirectory, null, config);
        Assert.assertTrue(
                Util.expectLines(server,
                                 "The Kernel is ready",
                                 "SwordWorld: application is ready"));
    }
    
    
}
