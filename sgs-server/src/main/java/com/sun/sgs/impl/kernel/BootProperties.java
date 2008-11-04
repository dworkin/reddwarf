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
 * This class contains filenames that are used during Kernel
 * bootup to locate application configuration files.
 */
class BootProperties {
    
    /**
     * the standard location for the home directory properties config file
     * which can be used to override any properties for the application
     */ 
    public static final String DEFAULT_HOME_CONFIG_FILE = ".sgs.properties";
    
    /**
     * The standard location to look for application properties config file
     */
    public static final String DEFAULT_APP_PROPERTIES = 
            "META-INF/app.properties";

}
