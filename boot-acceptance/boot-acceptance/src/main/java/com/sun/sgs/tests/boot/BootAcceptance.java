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
    private static File distribution;
    
    private File testDirectory;

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
        
        JUnitCore.runClasses(BootAcceptance.class);
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

        File d  = new File(tempDir + BootAcceptance.TEST_DIR);
        if(d.exists()) {
            Assert.fail("Unable to create test directory " + 
                        "that already exists : " + d);
        }
        
        Assert.assertTrue(d.mkdirs());
        this.testDirectory = d;
        Util.unzip(new ZipFile(distribution), testDirectory);
    }
    
    
    
    /**
     * Destroy the temporary test directory
     */
    @After
    public void removeTestDirectory() throws Exception {
        Assert.assertTrue(testDirectory.delete());
    }
    
    @Test
    public void test() {
        
    }
    
}
