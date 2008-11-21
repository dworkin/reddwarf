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
import java.util.ArrayList;
import java.util.jar.JarFile;

/**
 * Bootstraps and launches a Project Darkstar server.
 */
public final class Boot {
    private static final Logger logger = Logger.getLogger(Boot.class.getName());
    
    /**
     * This class should not be instantiated.
     */
    private Boot() {
        
    }

    /**
     * Main-line method that bootstraps startup of a Project Darkstar server.
     * <p>
     * If a single argument is given on the command line, the value of
     * the argument is assumed to be a filename.  This file is used to 
     * specify a set of configuration properties required in order to locate
     * the required components to startup an application in a Project
     * Darkstar container.  If no argument is given on the command line, 
     * the filename is assumed to be at the location specified by the system 
     * resource {@link BootEnvironment.SGS_BOOT}.
     * <p>
     * The properties included in the configuration file must conform to
     * the rules allowed by {@link SubstitutionProperties}.
     * 
     * @param args optional filename of configuration file
     * @throws Exception if there is any problem booting up
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            logger.log(Level.SEVERE, "Invalid number of arguments");
            throw new IllegalArgumentException("Invalid number of arguments");
        }
        
        //load properties from configuration file
        SubstitutionProperties properties = new SubstitutionProperties();
        URL sgsBoot = null;
        try {
            if (args.length == 0) {
                sgsBoot = ClassLoader.getSystemClassLoader().
                        getResource(BootEnvironment.SGS_BOOT);
            } else {
                sgsBoot = new File(args[0]).toURI().toURL();
            }
            properties.load(sgsBoot.openStream());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to load initial configuration", e);
            throw e;
        }
        
        //determine SGS_HOME
        String sgsHome = properties.getProperty(BootEnvironment.SGS_HOME);
        if (sgsHome == null) {
            properties.clear();
            URL jarLocation = Boot.class.getProtectionDomain().getCodeSource().
                    getLocation();
            String jarPath = jarLocation.getPath();
            int jarFileIndex = jarPath.indexOf(BootEnvironment.SGS_JAR);
            if (jarFileIndex == -1) {
                logger.log(Level.SEVERE, "Unable to determine SGS_HOME");
                throw new IllegalStateException("Unable to determine SGS_HOME");
            } else {
                sgsHome = jarPath.substring(0, jarFileIndex - 1);
                properties.setProperty(BootEnvironment.SGS_HOME, sgsHome);
                //reload the properties so that the value for SGS_HOME
                //is interpolated correctly in any other variables
                try {
                    properties.load(sgsBoot.openStream());
                } catch (IOException e) {
                    logger.log(Level.SEVERE, 
                               "Unable to load initial configuration", e);
                    throw e;
                }
            }
        }
        logger.log(Level.CONFIG, "SGS_HOME set to " + sgsHome);
        
        //load defaults for missing properties
        configureDefaultProperties(properties);
        
        //get the java executable
        String javaCmd = "java";
        String javaHome = properties.getProperty(BootEnvironment.JAVA_HOME);
        if (javaHome != null) {
            javaCmd = javaHome + File.separator + "jre" + 
                    File.separator + "bin" + File.separator + javaCmd;
        }
        
        //get the java options
        String javaOpts = properties.getProperty(BootEnvironment.JAVA_OPTS, "");
        
        //build the full execute path
        String execute = javaCmd + 
                " -cp " + bootClassPath(properties) +
                " -Djava.library.path=" + bootNativePath(properties) +
                " -Djava.util.logging.config.file=" + 
                properties.getProperty(BootEnvironment.SGS_LOGGING) +
                " " + javaOpts +
                " " + bootCommandLineProps(properties) +
                " " + BootEnvironment.KERNEL_CLASS +
                " " + properties.getProperty(BootEnvironment.SGS_PROPERTIES);
        List<String> executeCmd = Arrays.asList(execute.split("\\s+"));
        
        //build the process
        ProcessBuilder pb = new ProcessBuilder(executeCmd);
        pb.directory(
                new File(properties.getProperty(BootEnvironment.SGS_HOME)));
        pb.redirectErrorStream(true);
        
        //get the output stream
        PrintStream output = System.out;
        String logFile = properties.getProperty(BootEnvironment.SGS_LOGFILE);
        if (logFile != null) {
            try {
                //attempt to create any necessary parent directories
                //for the log file
                File log = new File(logFile);
                File parentDir = log.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                //create a stream for the log file
                output = new PrintStream(new FileOutputStream(logFile), true);
                logger.log(Level.INFO, "Redirecting log output to: " + logFile);
            } catch (FileNotFoundException e) {
                logger.log(Level.SEVERE, "Unable to open log file", e);
                throw e;
            }
        }

        //run the process
        try {
            Process p = pb.start();
            Thread t = new Thread(new ProcessOutputReader(p, output));
            t.start();
            t.join();
            p.waitFor();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to start process", e);
            throw e;
        } catch (InterruptedException i) {
            logger.log(Level.WARNING, "Thread interrupted", i);
        } finally {
            output.close();
        }
    }
    
    /**
     * Loads default values for the given set of properties if any
     * required properties are missing.
     * 
     * @param properties the set of boot configuration properties
     */
    private static void configureDefaultProperties(
            SubstitutionProperties properties) {
        
        //load defaults for any missing properties
        if (properties.getProperty(BootEnvironment.SGS_DEPLOY) == null) {
            properties.setProperty(BootEnvironment.SGS_DEPLOY,
                                   BootEnvironment.DEFAULT_SGS_DEPLOY);
        }
        if (properties.getProperty(BootEnvironment.SGS_LOGGING) == null) {
            properties.setProperty(BootEnvironment.SGS_LOGGING,
                                   BootEnvironment.DEFAULT_SGS_LOGGING);
        }
        if (properties.getProperty(BootEnvironment.SGS_PROPERTIES) == null) {
            properties.setProperty(BootEnvironment.SGS_PROPERTIES,
                                   BootEnvironment.DEFAULT_SGS_PROPERTIES);
        }
        if (properties.getProperty(BootEnvironment.BDB_TYPE) == null) {
            properties.setProperty(BootEnvironment.BDB_TYPE,
                                   BootEnvironment.DEFAULT_BDB_TYPE);
        }
        
        //autodetect BDB libraries if necessary
        if (properties.getProperty(BootEnvironment.BDB_NATIVES) == null) {
            String family = System.getProperty("os.family");
            String name = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");

            String bdb = null;
            if (name.equals("Linux") && arch.equals("i386")) {
                bdb = BootEnvironment.DEFAULT_BDB_LINUX_X86;
            } else if (name.equals("Linux") &&
                    (arch.equals("x86_64") || arch.equals("amd64"))) {
                bdb = BootEnvironment.DEFAULT_BDB_LINUX_X86_64;
            } else if (family.equals("mac") &&
                    (arch.equals("i386") || arch.equals("x86_64"))) {
                bdb = BootEnvironment.DEFAULT_BDB_MACOSX_X86;
            } else if (family.equals("mac") && arch.equals("ppc")) {
                bdb = BootEnvironment.DEFAULT_BDB_MACOSX_PPC;
            } else if (name.equals("SunOS") && arch.equals("sparc")) {
                bdb = BootEnvironment.DEFAULT_BDB_SOLARIS_SPARC;
            } else if (name.equals("SunOS") && arch.equals("x86")) {
                bdb = BootEnvironment.DEFAULT_BDB_SOLARIS_X86;
            } else if (family.equals("windows")) {
                bdb = BootEnvironment.DEFAULT_BDB_WIN32_X86;
            } else {
                logger.log(Level.SEVERE, "Unsupported platform: \n" +
                           "Family: " + family + "\n" +
                           "Name: " + name + "\n" +
                           "Arch: " + arch);
                throw new IllegalStateException("Unsupported platform");
            }
            properties.setProperty(BootEnvironment.BDB_NATIVES, bdb);
        }
    }
    
    /**
     * Constructs a classpath to be used when running the Project Darkstar
     * kernel.  The classpath consists of any jar files that live directly
     * in the {@code $SGS_HOME/lib}
     * directory.  It also recursively includes jar files from the
     * {@code $SGS_DEPLOY} directory. <p>
     * 
     * Additionally, files included in the path from the {@code $SGS_HOME/lib}
     * directory are filtered based on the value of {@code $BDB_TYPE}.
     * <ul>
     * <li>If the value of {@code $BDB_TYPE} is equal to {@code db}, any jar
     * files in {@code $SGS_HOME/lib} that begin with "je-" are excluded 
     * from the path.</li>
     * <li>If the value of {@code $BDB_TYPE} is equal to {@code je}, any jar
     * files in {@code $SGS_HOME/lib} that begin with "db-" are excluded
     * from the path.</li>
     * <li>If the value of {@code $BDB_TYPE} is equal to anything else, any jar
     * files in {@code SGS_HOME/lib} that being with "db-" OR "je-" are
     * excluded from the path.</li>
     * </ul>
     * 
     * @param env environment with SGS_HOME set
     * @return classpath to use to run the kernel
     */
    private static String bootClassPath(Properties env) {
        //determine SGS_HOME
        String sgsHome = env.getProperty(BootEnvironment.SGS_HOME);
        if (sgsHome == null) {
            return "";
        }
        
        //locate SGS_HOME directory
        File sgsHomeDir = new File(sgsHome);
        if (!sgsHomeDir.isDirectory()) {
            return "";
        }
        StringBuffer buf = new StringBuffer();
        
        //determine BDB_TYPE
        String bdbType = env.getProperty(BootEnvironment.BDB_TYPE);
        String filter = "^(db-|je-).*";
        if ("db".equals(bdbType)) {
            filter = "^(je-).*";
        } else if ("je".equals(bdbType)) {
            filter = "^(db-).*";
        }

        //add jars from SGS_HOME/lib, excluding the filtered bdb jar(s)
        File sgsLibDir = new File(sgsHome + File.separator + "lib");
        for (File sgsJar : sgsLibDir.listFiles()) {
            if (sgsJar.isFile() && sgsJar.getName().endsWith(".jar") &&
                    !sgsJar.getName().matches(filter)) {
                if (buf.length() != 0) {
                    buf.append(File.pathSeparator + sgsJar.getAbsolutePath());
                } else {
                    buf.append(sgsJar.getAbsolutePath());
                }
            }
        }
        
        //recursively add jars from SGS_DEPLOY
        File sgsDeployDir = new File(
                env.getProperty(BootEnvironment.SGS_DEPLOY));
        List<File> jars = new ArrayList<File>();
        int appPropsFound = appJars(sgsDeployDir, jars);
        if (appPropsFound == 0) {
            logger.log(Level.WARNING, "No application jar found with a " +
                       BootEnvironment.DEFAULT_APP_PROPERTIES +
                       " configuration file in the " +
                       sgsDeployDir + " directory");
        }
        if (appPropsFound > 1) {
            logger.log(Level.SEVERE, "Multiple application jars " +
                       "found with a " +
                       BootEnvironment.DEFAULT_APP_PROPERTIES +
                       " configuration file in the " +
                       sgsDeployDir + " directory");
            throw new IllegalStateException("Multiple application jars " +
                       "found with a " +
                       BootEnvironment.DEFAULT_APP_PROPERTIES +
                       " configuration file in the " +
                       sgsDeployDir + " directory");
        }
        for (File jar : jars) {
            if (buf.length() != 0) {
                buf.append(File.pathSeparator + jar.getAbsolutePath());
            } else {
                buf.append(jar.getAbsolutePath());
            }
        }

        //include the additional classpath if specified
        String addPath = env.getProperty(BootEnvironment.CUSTOM_CLASSPATH_ADD);
        if (addPath != null) {
            if (buf.length() != 0) {
                buf.append(File.pathSeparator + addPath);
            } else {
                buf.append(addPath);
            }
        }

        return buf.toString();
    }

    /**
     * Constructs a path to be used as the {@code java.library.path}
     * when running the Project Darkstar kernel.  The path combines the string 
     * specified by the {@code $BDB_NATIVES } property with the string specified
     * by the {@code $CUSTOM_NATIVES} property.  Additionally, if the
     * {@code BDB_TYPE} property is not set to {@code db}, only
     * the {@code $CUSTOM_NATIVES} property is used for the path.
     * 
     * @param env the environment
     * @return path to use as the {@code java.library.path} in the kernel
     */
    private static String bootNativePath(Properties env) {
        String type = env.getProperty(BootEnvironment.BDB_TYPE);
        String bdb = env.getProperty(BootEnvironment.BDB_NATIVES);
        String custom = env.getProperty(BootEnvironment.CUSTOM_NATIVES);
        StringBuffer buf = new StringBuffer();
        
        if (type.equals("db")) {
            buf.append(bdb);
            if (custom != null && !custom.equals("")) {
                buf.append(File.pathSeparator + custom);
            }
        } else {
            if (custom != null && !custom.equals("")) {
                buf.append(custom);
            }
        }
        
        return buf.toString();
    }
    
    /**
     * Constructs a set of additional command line properties that are to
     * be used when running the Project Darkstar kernel.  Specifically, this
     * method specifies a value for the property
     * {@code com.sun.sgs.impl.service.data.store.db.environment.class} in
     * order to specify the bdb flavor that is being used.  It is dependent
     * on the value of the {@code $BDB_TYPE} environment property.
     * 
     * <ul>
     * <li>If the value of {@code $BDB_TYPE} is equal to {@code db}, then
     * {@code com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment} is
     * used.</li>
     * <li>If the value of {@code $BDB_TYPE} is equal to {@code je}, then
     * {@code com.sun.sgs.impl.service.data.store.db.je.JeEnvironment} is
     * used.</li>
     * <li>If the value of {@code BDB_TYPE} is equal to anything else, no
     * value is specified.</li>
     * </ul>
     * 
     * @param env the environment
     * @return additional set of properties to be passed to the command line
     */
    private static String bootCommandLineProps(Properties env) {
        String type = env.getProperty(BootEnvironment.BDB_TYPE);
        String line = 
                "-Dcom.sun.sgs.impl.service.data.store.db.environment.class";
        
        if (type.equals("db")) {
            return line + "=" +
                    "com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment";
        } else if (type.equals("je")) {
            return line + "=" +
                    "com.sun.sgs.impl.service.data.store.db.je.JeEnvironment";
        } else {
            return "";
        }
    }
    
    /**
     * Helper method that recursively searches the given directory and adds
     * any jar files found to the jars list.
     * 
     * @param directory directory to search for jar files
     * @param jars list of Files to add any jar files found
     * @return the number of jar files found that have a
     *         {@link BootEnvironment.DEFAULT_APP_PROPERTIES} file in them
     */
    private static int appJars(File directory, List<File> jars) {
        int appPropsFound = 0;
        if (directory.isDirectory() && directory.canRead()) {
            for (File f : directory.listFiles()) {
                if (f.isFile() && f.getName().endsWith(".jar")) {
                    try {
                        JarFile jar = new JarFile(f);
                        jars.add(f);
                        if (jar.getJarEntry(
                                BootEnvironment.DEFAULT_APP_PROPERTIES) !=
                                null) {
                            appPropsFound++;
                        }
                    } catch (IOException e) {
                        //not a jar file, log and ignore
                        logger.log(Level.WARNING, "File " + 
                                   f.getAbsolutePath() +
                                   " is not a jar file");
                    }
                } else if (f.isDirectory()) {
                    appPropsFound += appJars(f, jars);
                }
            }
        }

        return appPropsFound;
    }
    
}
