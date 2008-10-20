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
import java.io.File;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.Arrays;

/**
 * Bootstraps and launches a Project Darkstar server
 */
public class Boot {
    private static final Logger logger = Logger.getLogger(Boot.class.getName());

    public static void main(String[] args) {
        //load properties from configuration file
        Properties properties = new SubstitutionProperties();
        try {
            URL sgsBoot = ClassLoader.getSystemClassLoader().
                    getResource(BootEnvironment.SGS_BOOT);
            properties.load(sgsBoot.openStream());
        } catch(Exception e) {
            logger.log(Level.SEVERE, "Unable to load initial configuration", e);
            System.exit(1);
        }
        
        //determine SGS_HOME
        String sgsHome = properties.getProperty(BootEnvironment.SGS_HOME);
        if(sgsHome == null) {
            URL jarLocation = Boot.class.getProtectionDomain().getCodeSource().getLocation();
            String jarPath = jarLocation.getPath();
            int jarFileIndex = jarPath.indexOf(BootEnvironment.SGS_JAR);
            if(jarFileIndex == -1) {
                logger.log(Level.SEVERE, "Unable to determine SGS_HOME");
                System.exit(1);
            }
            else {
                sgsHome = jarPath.substring(0, jarFileIndex - 1);
                properties.setProperty(BootEnvironment.SGS_HOME, sgsHome);
            }
        }
        logger.log(Level.CONFIG, "SGS_HOME set to "+sgsHome);
        
        //load defaults for any missing properties
        if(properties.getProperty(BootEnvironment.SGS_DEPLOY) == null) {
            properties.setProperty(BootEnvironment.SGS_DEPLOY,
                                   BootEnvironment.DEFAULT_SGS_DEPLOY);
        }
        if(properties.getProperty(BootEnvironment.SGS_LOGGING) == null) {
            properties.setProperty(BootEnvironment.SGS_LOGGING,
                                   BootEnvironment.DEFAULT_SGS_LOGGING);
        }
        if(properties.getProperty(BootEnvironment.SGS_PROPERTIES) == null) {
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
        
        //get the java executable
        String javaCmd = "java";
        String javaHome = properties.getProperty(BootEnvironment.JAVA_HOME);
        if(javaHome != null)
            javaCmd = javaHome + File.separator + "jre" + 
                    File.separator + "bin" + File.separator + javaCmd;
        
        //get the java options
        String javaOpts = properties.getProperty(BootEnvironment.JAVA_OPTS, "");
        
        //build the classpath
        String classpath = bootClassPath(properties);

        //build the full execute path
        String execute = javaCmd + 
                " -cp " + classpath +
                " -Djava.util.logging.config.file=" + properties.getProperty(BootEnvironment.SGS_LOGGING) +
                " -Djava.library.path=" + properties.getProperty(BootEnvironment.BDB_NATIVES) +
                " " + javaOpts +
                " " + BootEnvironment.KERNEL_CLASS;
        List<String> executeCmd = Arrays.asList(execute.split("\\s+"));
        
        //build the process
        ProcessBuilder pb = new ProcessBuilder(executeCmd);
        pb.environment().put(BootEnvironment.SGS_HOME, 
                             properties.getProperty(BootEnvironment.SGS_HOME));
        pb.environment().put(BootEnvironment.SGS_DEPLOY, 
                             properties.getProperty(BootEnvironment.SGS_DEPLOY));
        pb.environment().put(BootEnvironment.SGS_PROPERTIES,
                             properties.getProperty(BootEnvironment.SGS_PROPERTIES));
        pb.directory(new File(properties.getProperty(BootEnvironment.SGS_HOME)));
        pb.redirectErrorStream(true);
        
        //get the output stream
        PrintStream output = System.out;
        String logFile = properties.getProperty(BootEnvironment.SGS_LOGFILE);
        if(logFile != null) {
            try {
                output = new PrintStream(new FileOutputStream(logFile), true);
                logger.log(Level.INFO, "Redirecting log output to: "+logFile);
            } catch(FileNotFoundException e) {
                logger.log(Level.SEVERE, "Unable to open log file", e);
                System.exit(1);
            }
        }

        //run the process
        try {
            Process p = pb.start();
            Thread t = new Thread(new ProcessOutputReader(p, output));
            t.start();
            t.join();
            p.waitFor();
        } catch(IOException e) {
            logger.log(Level.SEVERE, "Unable to start process", e);
            System.exit(1);
        } catch(InterruptedException i) {
            logger.log(Level.WARNING, "Thread interrupted", i);
        }

    }
    
    /**
     * Constructs a classpath to be used when running the Project Darkstar
     * kernel.  The classpath consists of any jar files that live directly
     * in subdirectories of the $SGS_HOME directory from the environment
     * (with the exception of the $SGS_HOME/sgs-server directory).
     * It also contains any jar files in the $SGS_HOME/sgs-server/lib
     * directory.
     * 
     * @param env environment with SGS_HOME set
     * @return classpath to use to run the kernel
     */
    private static String bootClassPath(Properties env) {
        //determine SGS_HOME
        String sgsHome = env.getProperty(BootEnvironment.SGS_HOME);
        if(sgsHome == null)
            return "";
        
        //locate SGS_HOME directory
        File sgsHomeDir = new File(sgsHome);
        if(!sgsHomeDir.isDirectory())
            return "";
        
        //build classpath from SGS_HOME subdirectories
        StringBuffer buf = new StringBuffer();
        for(File f : sgsHomeDir.listFiles()) {
            if(f.isDirectory() && !f.getName().equals("sgs-server")) {
                for(File jar : f.listFiles()) {
                    if(jar.getName().endsWith(".jar")) {
                        if(buf.length() != 0)
                            buf.append(File.pathSeparator + jar.getAbsolutePath());
                        else
                            buf.append(jar.getAbsolutePath());
                    }
                }
            }
        }
        
        //add jars from SGS_HOME/sgs-server
        File sgsLibDir = new File(sgsHome + File.separator + 
                                  "sgs-server" + File.separator + "lib");
        for (File sgsJar : sgsLibDir.listFiles()) {
            if(sgsJar.isFile() && sgsJar.getName().endsWith(".jar")) {
                if(buf.length() != 0)
                    buf.append(File.pathSeparator + sgsJar.getAbsolutePath());
                else
                    buf.append(sgsJar.getAbsolutePath());
            }
        }
        
        return buf.toString();
    }
    
}
