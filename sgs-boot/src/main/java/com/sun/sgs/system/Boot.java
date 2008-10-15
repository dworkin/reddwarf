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

package com.sun.sgs.system;

import java.net.URL;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bootstraps and launches a Project Darkstar server
 */
public class Boot {
    private static Logger logger = Logger.getLogger(Boot.class.getName());

    public static void main(String[] args) {
        //load properties from configuration file
        Properties properties = new SubstitutionProperties();
        try {
            URL sgs_boot = ClassLoader.getSystemClassLoader().
                    getResource(BootEnvironment.SGS_BOOT);
            properties.load(sgs_boot.openStream());
        } catch(Exception e) {
            logger.log(Level.SEVERE, "Unable to load initial configuration", e);
            System.exit(1);
        }
        
        //ensure that SGS_HOME is set
        String sgs_home = properties.getProperty(BootEnvironment.SGS_HOME);
        if(sgs_home == null) {
            logger.log(Level.SEVERE, BootEnvironment.SGS_HOME +
                       " is not specified.");
            System.exit(1);
        }
        
        //load defaults for any missing properties
        if(properties.getProperty(BootEnvironment.SGS_DEPLOY) == null) {
            properties.setProperty(BootEnvironment.SGS_DEPLOY,
                                   BootEnvironment.DEFAULT_SGS_DEPLOY);
        }
        if(properties.getProperty(BootEnvironment.SGS_LOGGING) == null) {
            properties.setProperty(BootEnvironment.SGS_LOGGING,
                                   BootEnvironment.DEFAULT_SGS_LOGGING);
        }
        if(properties.getProperty(BootEnvironment.SGS_LOGGING) == null) {
            properties.setProperty(BootEnvironment.SGS_PROPERTIES,
                                   BootEnvironment.DEFAULT_SGS_PROPERTIES);
        }
        
        //autodetect BDB libraries if necessary
        if(properties.getProperty(BootEnvironment.BDB_NATIVES) == null) {
            String family = System.getProperty("os.family");
            String name = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");

            String bdb = null;
            if (name.equals("Linux") && arch.equals("i386"))
                bdb = BootEnvironment.DEFAULT_BDB_LINUX_X86;
            else if(name.equals("Linux") && (arch.equals("x86_64") || arch.equals("amd64")))
                bdb = BootEnvironment.DEFAULT_BDB_LINUX_X86_64;
            else if(family.equals("mac") && (arch.equals("i386") || arch.equals("x86_64")))
                bdb = BootEnvironment.DEFAULT_BDB_MACOSX_X86;
            else if(family.equals("mac") && arch.equals("ppc"))
                bdb = BootEnvironment.DEFAULT_BDB_MACOSX_PPC;
            else if(name.equals("SunOS") && arch.equals("sparc"))
                bdb = BootEnvironment.DEFAULT_BDB_SOLARIS_SPARC;
            else if(name.equals("SunOS") && arch.equals("x86"))
                bdb = BootEnvironment.DEFAULT_BDB_SOLARIS_X86;
            else if(family.equals("windows"))
                bdb = BootEnvironment.DEFAULT_BDB_WIN32_X86;
            else {
                logger.log(Level.SEVERE, "Unsupported platform: \n" +
                           "Family: " + family + "\n" +
                           "Name: " + name + "\n" +
                           "Arch: " + arch);
                System.exit(1);
            }
                
            properties.setProperty(BootEnvironment.BDB_NATIVES, bdb);
        }
        
        
        System.out.print(properties);
    }
    
}
