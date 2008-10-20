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

package com.sun.sgs.impl.kernel;

/**
 * This class contains system property keys that are used during Kernel
 * bootup to locate application configuration files. <p>
 * 
 * If the Kernel is started without being given an application
 * <code>Properties</code> file, the configuration properties for the
 * application are determined by locating files using the system
 * property keys defined here.
 */
class BootProperties {
    
    /**
     * The system property key to determine the home directory of the
     * Project Darkstar distribution installation
     */
    public static final String SGS_HOME = "SGS_HOME";
    
    /**
     * The system property key of the directory which contains application
     * jar files.  Any jar files in the SGS_DEPLOY directory will
     * be included in a custom classloader upon bootup of the Kernel so
     * that the application can locate its required classes.  Additionally,
     * one and only one of the jar files in this directory should contain
     * a properties file with the name equal to the value of
     * {@link DEFAULT_APP_PROPERTIES}.  This configuration file will
     * automatically be used as the property configuration file of the
     * application.
     */
    public static final String SGS_DEPLOY = "SGS_DEPLOY";
    
    /**
     * The system property key of the config file which contains a
     * standard base set of default properties to use when running
     * applications.  Any properties that are not included in 
     * {@link DEFAULT_APP_PROPERTIES} will have their values
     * resolved to values specified in this file.
     */
    public static final String SGS_PROPERTIES = "SGS_PROPERTIES";
    
    /**
     * the standard location for the home directory properties config file
     * which can be used to override any properties specified
     * in the SGS_PROPERTIES or application specific properties
     * config files.
     */ 
    public static final String DEFAULT_HOME_CONFIG_FILE = ".sgs.properties";
    
    /**
     * The standard location to look for application properties config
     * file in jars from the SGS_DEPLOY directory.
     */
    public static final String DEFAULT_APP_PROPERTIES = 
            "META-INF/app.properties";

}
