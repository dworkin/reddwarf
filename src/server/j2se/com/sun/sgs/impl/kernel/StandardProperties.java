
package com.sun.sgs.impl.kernel;


/**
 * This class contains the standard property keys that the kernel looks for
 * (and may provide) on startup.
 * <p>
 * Note that when the kernel starts, it is given a list of applications to
 * run. For each application named <code>{NAME}</code>, the kernel will
 * provide any key starting with <code>com.sun.sgs.{NAME}</code> into the
 * namespace <code>com.sun.sgs.app</code> for the scope of the application
 * <code>{NAME}</code>. For instance, for the application <code>MyApp</code>,
 * the value of the key <code>com.sun.sgs.MyApp.foo</code> will be provided
 * to the application <code>MyApp</code> by the key
 * <code>com.sun.sgs.app.foo</code>.
 * <p>
 * Default values can provided for all applications by using one of the
 * properties specified in this class, and specific application properties
 * can be provided by replacing <code>app</code> with the name of the
 * application in the property key.
 */
public class StandardProperties {

    // the root of all the properties
    private static final String NS = "com.sun.sgs";

    /**
     * An optional key specifying a file containing properties. If set,
     * this must point to the location of a properties file. Each property
     * within that file is provided to the system and all applications. Each
     * property may be overriden by the system properties provided at
     * startup.
     */
    public static final String CONFIG_FILE = NS + "config.file";

    /**
     * An automatically generated key specifying the name of an applcation.
     * This should not be provided to the kernel, since it will override
     * any provided value.
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
