/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;


/**
 * This class contains the standard property keys that the kernel looks for
 * (and may provide) on startup.
 * <p>
 * Note that when the kernel starts, it is given a list of application
 * <code>Properties</code> files, each of which represents a single
 * application to run. For each application, any of the property keys
 * identified here (except for <code>CONFIG_FILE</code>) will set the
 * associated behavior for that application. If no value is provided
 * for a given key, then the default or system-provided value is used.
 * Note that some keys are required to have values, specifically
 * <code>APP_NAME</code>, <code>APP_ROOT</code>, <code>APP_LISTENER</code>,
 * and <code>APP_PORT</code>.
 * <p>
 * Default values can be provided for all applications by using any of the
 * properties specified in this class at the system level (i.e., in a
 * system config file or as a system property). The one exception is
 * <code>APP_NAME</code>, which may not be specified at the system level,
 * and must instead be specified in each application's <code>Properties</code>
 * configuration file.
 */
public class StandardProperties {

    // the root of all the properties
    private static final String NS = "com.sun.sgs.";

    /**
     * An optional key specifying a file containing system properties. If set,
     * this must point to the location of a properties file. Each property
     * within that file is provided to the system and all applications. Each
     * property may be overriden by a system property provided at startup, or
     * by a property for an individual application.
     */
    public static final String CONFIG_FILE = NS + "config.file";

    /**
     * A required key specifying the name of an applcation.
     */
    public static final String APP_NAME = NS + "app.name";
    
    /**
     * A required key specifying the root directory for an application.
     */
    public static final String APP_ROOT = NS + "app.root";

    /**
     * A required key specifying the <code>AppListener</code> for an
     * application.
     */
    public static final String APP_LISTENER = NS + "app.listener";

    /**
     * A required key specifying the listening port for an application.
     */
    public static final String APP_PORT = NS + "app.port";

    /**
     * An optional key specifying a specific class to use for an application's
     * <code>DataService</code>.
     */
    public static final String DATA_SERVICE = NS + "app.dataService";

    /**
     * An optional key specifying a specific class to use for an application's
     * <code>DataManager</code>.
     */
    public static final String DATA_MANAGER = NS + "app.dataManager";

    /**
     * An optional key specifying a specific class to use for an application's
     * <code>TaskService</code>.
     */
    public static final String TASK_SERVICE = NS + "app.taskService";

    /**
     * An optional key specifying a specific class to use for an application's
     * <code>TaskManager</code>.
     */
    public static final String TASK_MANAGER = NS + "app.taskManager";

    /**
     * An optional key specifying a specific class to use for an application's
     * <code>ClientSessionService</code>.
     */
    public static final String CLIENT_SESSION_SERVICE =
        NS + "app.clientSessionService";

    /**
     * An optional key specifying a specific class to use for an application's
     * <code>ChannelService</code>.
     */
    public static final String CHANNEL_SERVICE = NS + "app.channelService";

    /**
     * An optional key specifying a specific class to use for an application's
     * <code>ChannelManager</code>.
     */
    public static final String CHANNEL_MANAGER = NS + "app.channelManager";

    /**
     * An optional colon-separated key that specifies extra services to use
     * with an application.
     */
    public static final String SERVICES = NS + "app.services";

    /**
     * An optional colon-separated key that specifies extra managers to use
     * with an application. This must contain the same number of classes as
     * <code>SERVICES</code>. Each manager in this list will be paired with
     * the cooresponding <code>Service</code> from the <code>SERVICES</code>
     * list. To specify a <code>Service</code> with no manager, leave the
     * appropriate element in the list empty. E.g.:
     * <pre>
     *    -DSERVICES=S1:S2:S3 -DMANAGERS=M1::M3
     * </pre>
     * This specifies three <code>Service</code>s where S2 has no
     * associated manager.
     */
    public static final String MANAGERS = NS + "app.managers";

    /**
     * An optional colon-separated key that specifies which
     * <code>IdentityAuthenticator</code>s to use with an application. The
     * order here is used to define order of precedence when authenticating
     * an identity.
     */
    public static final String AUTHENTICATORS = NS + "app.authenticators";

}
