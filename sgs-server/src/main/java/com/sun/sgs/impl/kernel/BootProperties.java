/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.kernel;

/**
 * This class contains property and file names that are used during Kernel
 * bootup to locate application configuration files.
 */
final class BootProperties {
    
    /**
     * This class should not be instantiated
     */
    private BootProperties() {
        
    }
    
    /**
     * The standard location for the home directory properties config file
     * which can be used to override any properties for the application.
     */ 
    static final String DEFAULT_HOME_CONFIG_FILE = ".sgs.properties";
    
    /**
     * The standard resource location to look for application
     * properties configuration.
     */
    static final String DEFAULT_APP_PROPERTIES = 
            "META-INF/app.properties";
    
    /**
     * The standard resource location to look for application logging 
     * properties configuration.
     */
    static final String DEFAULT_LOG_PROPERTIES =
            "META-INF/logging.properties";

    /**
     * The property used to specify an optional properties file used to
     * configure extension libraries.
     */
    static final String EXTENSION_FILE_PROPERTY = "com.sun.sgs.ext.properties";

    /** The property used to specify services from extension libraries. */
    static final String EXTENSION_SERVICES_PROPERTY =
        "com.sun.sgs.ext.services";

    /** The property used to specify managers from extension libraries. */
    static final String EXTENSION_MANAGERS_PROPERTY =
        "com.sun.sgs.ext.managers";

    /**
     * The property used to specify service node types from extension
     * libraries.
     */
    static final String EXTENSION_SERVICE_NODE_TYPES_PROPERTY =
        "com.sun.sgs.ext.services.node.types";

    /** The property used to specify authenticators from extension libraries. */
    static final String EXTENSION_AUTHENTICATORS_PROPERTY =
        "com.sun.sgs.ext.authenticators";

    /**
     * The property used to specify profile listeners from extension
     * libraries.
     */
    static final String EXTENSION_PROFILE_LISTENERS_PROPERTY =
        "com.sun.sgs.ext.kernel.profile.listeners";

}
