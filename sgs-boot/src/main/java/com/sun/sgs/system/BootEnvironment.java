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

import java.io.File;

/**
 * Defines environment variables used by the bootstrapper
 * to locate and configure the necessary environment pieces
 * to launch a Project Darkstar Server.
 */
public final class BootEnvironment {
    
    /**
     * This class should not be instantiated.
     */
    private BootEnvironment() {
        
    }
    
    /**
     * Default location of the bootstrapper jar relative to {@code SGS_HOME}.
     */
    public static final String SGS_JAR = "bin/sgs-boot.jar";
    
    /**
     * Name of the properties file to locate and retrieve properties
     * for the environment.
     */
    public static final String SGS_BOOT = "sgs-boot.properties";

    /**
     * Denotes the installation directory for the Project Darkstar server.
     */
    public static final String SGS_HOME = "SGS_HOME";
    
    /**
     * The directory where deployed applications should place jar files
     * and application properties files.
     */
    public static final String SGS_DEPLOY = "SGS_DEPLOY";
    
    /**
     * The properties file used to configure the Project Darkstar kernel.
     * This file should be fed to the Project Darkstar Kernel.
     */
    public static final String SGS_PROPERTIES = "SGS_PROPERTIES";
    
    /**
     * The logging properties file for the Project Darkstar server.
     */
    public static final String SGS_LOGGING = "SGS_LOGGING";
    
    /**
     * The name of the log file to send output to.
     */
    public static final String SGS_LOGFILE = "SGS_LOGFILE";
    
    /**
     * A toggle used to specify which flavor of Berkeley DB is to be
     * used by the application.
     */
    public static final String BDB_TYPE = "BDB_TYPE";
    
    /**
     * The location of the Berkeley DB natives to include as part
     * of the {@code java.library.path}.
     */
    public static final String BDB_NATIVES = "BDB_NATIVES";
    
    /**
     * A custom set of native library directories to include as part of the
     * {@code java.library.path}.
     */
    public static final String CUSTOM_NATIVES = "CUSTOM_NATIVES";
    
    /**
     * A custom set of additional jar files to include on the classpath.
     */
    public static final String CUSTOM_CLASSPATH_ADD = "CUSTOM_CLASSPATH_ADD";
    
    /**
     * Port to listen for SHUTDOWN command.
     */
    public static final String SHUTDOWN_PORT = "SHUTDOWN_PORT";
    
    /**
     * Location of the JDK to use when booting up the Kernel.
     */
    public static final String JAVA_HOME = "JAVA_HOME";
    
    /**
     * Command line arguments for the JVM.
     */
    public static final String JAVA_OPTS = "JAVA_OPTS";
    
    /**
     * The default value for the {@code SGS_DEPLOY} property.
     */
    public static final String DEFAULT_SGS_DEPLOY = 
            "${SGS_HOME}" + File.separator + "deploy";
    /**
     * The default value for the {@code SGS_PROPERTIES} property.
     */
    public static final String DEFAULT_SGS_PROPERTIES = 
            "${SGS_HOME}" + File.separator + "conf" + File.separator +
            "sgs-server.properties";
    /**
     * The default value for the {@code SGS_LOGGING} property.
     */
    public static final String DEFAULT_SGS_LOGGING = 
            "${SGS_HOME}" + File.separator + "conf" + File.separator +
            "sgs-logging.properties";
    /**
     * The default value for the {@code BDB_TYPE} property.
     */
    public static final String DEFAULT_BDB_TYPE = "db";
    /**
     * The standard location to look for application properties config
     * file in jars from the {@code SGS_DEPLOY} directory.
     */
    public static final String DEFAULT_APP_PROPERTIES = 
            "META-INF/app.properties";
    /**
     * The default value for the {@code DEFAULT_SHUTDOWN_PORT} property.
     */
    public static final String DEFAULT_SHUTDOWN_PORT = "1138";
    
    
    static final String DEFAULT_BDB_ROOT = 
            "${SGS_HOME}" + File.separator + "natives";
    static final String DEFAULT_BDB_LINUX_X86 = 
            DEFAULT_BDB_ROOT + File.separator + "linux-x86";
    static final String DEFAULT_BDB_LINUX_X86_64 = 
            DEFAULT_BDB_ROOT + File.separator + "linux-x86_64";
    static final String DEFAULT_BDB_MACOSX_X86 =
            DEFAULT_BDB_ROOT + File.separator + "macosx-x86";
    static final String DEFAULT_BDB_MACOSX_PPC = 
            DEFAULT_BDB_ROOT + File.separator + "macosx-ppc";
    static final String DEFAULT_BDB_SOLARIS_SPARC = 
            DEFAULT_BDB_ROOT + File.separator + "solaris-sparc";
    static final String DEFAULT_BDB_SOLARIS_X86 = 
            DEFAULT_BDB_ROOT + File.separator + "solaris-x86";
    static final String DEFAULT_BDB_WIN32_X86 = 
            DEFAULT_BDB_ROOT + File.separator + "win32-x86";
    
    
    static final String KERNEL_CLASS = "com.sun.sgs.impl.kernel.Kernel";
    
}
