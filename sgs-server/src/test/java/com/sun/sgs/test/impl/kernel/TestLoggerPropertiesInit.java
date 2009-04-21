/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.impl.kernel.LoggerPropertiesInit;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.lang.reflect.Method;
import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.runner.RunWith;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;

/**
 * Test the logging configuration initialization with the LoggerPropertiesInit
 * constructor.  This is configured as an integration test since it does
 * potentially problematic actions such as creating and deleting configuration
 * files.
 */
@IntegrationTest
@RunWith(FilteredNameRunner.class)
public class TestLoggerPropertiesInit
{
    private static String originalClass;
    private static String originalFile;
    private static Method init;

    /**
     * The configuration file used as java.util.logging.config.file in the tests
     */
    private static File configFile;
    /**
     * An already existing resource file that is used as the
     * defaultLogProperties resource in the tests
     */
    private static String resource = 
            "com/sun/sgs/test/impl/kernel/TestLoggerPropertiesInit.resource";
    
    @BeforeClass
    public static void saveLogManagerConfig() throws Exception {
        originalClass = System.getProperty("java.util.logging.config.class");
        originalFile = System.getProperty("java.util.logging.config.file");
        
        System.setProperty("java.util.logging.config.class",
                           "com.sun.sgs.impl.kernel.LoggerPropertiesInit");
        System.getProperties().remove("java.util.logging.config.file");
        
        init = LoggerPropertiesInit.class.getDeclaredMethod("init", 
                                                            String.class);
        init.setAccessible(true);
    }
    
    @BeforeClass
    public static void buildTempItems() throws Exception {
        configFile = File.createTempFile(
                TestLoggerPropertiesInit.class.getName(), "configFile");
        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new FileWriter(configFile)));
        writer.println(".level = SEVERE");
        writer.println("a.b.c.level = FINER");
        writer.println("d.e.f.level = FINEST");
        writer.close();
    }
    
    @Before
    public void clearLogManager() {
        LogManager.getLogManager().reset();
        System.getProperties().remove("java.util.logging.config.file");
    }
    
    @Test
    public void testInitNoResourceNoFile() throws Exception {
        init.invoke(LoggerPropertiesInit.class, "noSuchResource");
        Assert.assertEquals("INFO", 
                            LogManager.getLogManager().getProperty(".level"));
        Assert.assertNull(LogManager.getLogManager().getProperty("a.b.c.level"));
        Assert.assertNull(LogManager.getLogManager().getProperty("d.e.f.level"));
        Assert.assertNull(LogManager.getLogManager().getProperty("x.y.z.level"));
    }
    
    @Test
    public void testInitNoFile() throws Exception {
        init.invoke(LoggerPropertiesInit.class, resource);
        Assert.assertEquals("WARNING",
                            LogManager.getLogManager().getProperty(".level"));
        Assert.assertEquals("FINE",
                            LogManager.getLogManager().getProperty("a.b.c.level"));
        Assert.assertEquals("FINEST",
                            LogManager.getLogManager().getProperty("x.y.z.level"));
        Assert.assertNull(LogManager.getLogManager().getProperty("d.e.f.level"));
        
        Logger abc = Logger.getLogger("a.b.c");
        Logger xyz = Logger.getLogger("x.y.z");
        Assert.assertEquals(Level.FINE, abc.getLevel());
        Assert.assertEquals(Level.FINEST, xyz.getLevel());
    }

    @Test
    public void testInitNoResource() throws Exception {
        System.setProperty("java.util.logging.config.file",
                           configFile.getAbsolutePath());
        init.invoke(LoggerPropertiesInit.class, "noSuchResource");
        Assert.assertEquals("SEVERE",
                            LogManager.getLogManager().getProperty(".level"));
        Assert.assertEquals("FINER",
                            LogManager.getLogManager().getProperty("a.b.c.level"));
        Assert.assertEquals("FINEST",
                            LogManager.getLogManager().getProperty("d.e.f.level"));
        Assert.assertNull(LogManager.getLogManager().getProperty("x.y.z.level"));
    }
    
    @Test
    public void testInitResourceAndFile() throws Exception {
        System.setProperty("java.util.logging.config.file",
                           configFile.getAbsolutePath());
        init.invoke(LoggerPropertiesInit.class, resource);
        Assert.assertEquals("SEVERE",
                            LogManager.getLogManager().getProperty(".level"));
        
        Assert.assertEquals("FINER",
                            LogManager.getLogManager().getProperty("a.b.c.level"));
        Assert.assertEquals("FINEST",
                            LogManager.getLogManager().getProperty("d.e.f.level"));
        Assert.assertEquals("FINEST",
                            LogManager.getLogManager().getProperty("x.y.z.level"));
        
        Logger abc = Logger.getLogger("a.b.c");
        Logger def = Logger.getLogger("d.e.f");
        Logger xyz = Logger.getLogger("x.y.z");
        Assert.assertEquals(Level.FINER, abc.getLevel());
        Assert.assertEquals(Level.FINEST, def.getLevel());
        Assert.assertEquals(Level.FINEST, xyz.getLevel());
    }
    
    @AfterClass
    public static void cleanupTempItems() throws Exception {
        if (configFile != null) {
            configFile.delete();
        }
    }
    
    
    @AfterClass
    public static void restoreLogManagerConfig() throws Exception {
        if(originalFile != null) {
            System.setProperty("java.util.logging.config.file", originalFile);
        } else {
            System.getProperties().remove("java.util.logging.config.file");
        }
        
        if(originalClass != null) {
            System.setProperty("java.util.logging.config.class", originalClass);
        } else {
            System.getProperties().remove("java.util.logging.config.class");
        }
        
        LogManager.getLogManager().readConfiguration();
    }

}
