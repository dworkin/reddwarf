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
 * This class contains the standard property keys that the kernel looks for
 * (and may provide) on startup.
 * <p>
 * When when the kernel starts, it is given an application
 * <code>Properties</code> file. Any of the property keys identified here will
 * set the associated behavior for that application. If no value is provided
 * for a given key, then the default or system-provided value is used.
 * Note that some keys are required to have values, specifically
 * <code>APP_NAME</code>, <code>APP_ROOT</code>, <code>APP_LISTENER</code>,
 * and <code>APP_PORT</code>.
 * <p>
 * Default values can be provided for all applications by using any of the
 * properties specified in this class as a system property.
 * <p>
 * A deprecated property is <code>CONFIG_FILE</code>, which, if present,
 * will be combined with the application property file.
 */
public class StandardProperties {

    // the root of all the properties
    private static final String NS = "com.sun.sgs.";
    
    /**
     * A required key specifying the name of the application.
     */
    public static final String APP_NAME = NS + "app.name";
    
    /**
     * A required key specifying the root directory for the application.
     */
    public static final String APP_ROOT = NS + "app.root";

    /**
     * A required key specifying the <code>AppListener</code> for the
     * application.
     */
    public static final String APP_LISTENER = NS + "app.listener";

    /**
     * The value for the null <code>AppListener</code>, used to start an
     * application context with no running application.
     */
    public static final String APP_LISTENER_NONE = "NONE";

    /**
     * A key specifying the listening port for the application.  Required
     * unless a null <code>AppListener</code> is specified.
     */
    public static final String APP_PORT = NS + "app.port";

    /**
     * An optional key specifying a specific class to use for the
     * <code>DataService</code>.
     */
    public static final String DATA_SERVICE = NS + "dataService";

    /**
     * An optional key specifying a specific class to use for the
     * <code>DataManager</code>.
     */
    public static final String DATA_MANAGER = NS + "dataManager";

    /**
     * An optional key specifying a specific class to use for the
     * <code>NodeMappingService</code>.
     */
    public static final String NODE_MAPPING_SERVICE =
        NS + "nodeMappingService";
    /**
     * An optional key specifying a specific class to use for the
     * <code>TaskService</code>.
     */
    public static final String TASK_SERVICE = NS + "taskService";

    /**
     * An optional key specifying a specific class to use for the
     * <code>TaskManager</code>.
     */
    public static final String TASK_MANAGER = NS + "taskManager";

    /**
     * An optional key specifying a specific class to use for the
     * {@code WatchdogService}.
     */
    public static final String WATCHDOG_SERVICE = NS + "watchdogService";

    /**
     * An optional key specifying a specific class to use for the
     * <code>ClientSessionService</code>.
     */
    public static final String CLIENT_SESSION_SERVICE =
        NS + "clientSessionService";

    /**
     * An optional key specifying a specific class to use for the
     * <code>ChannelService</code>.
     */
    public static final String CHANNEL_SERVICE = NS + "channelService";

    /**
     * An optional key specifying a specific class to use for the
     * <code>ChannelManager</code>.
     */
    public static final String CHANNEL_MANAGER = NS + "channelManager";

    /**
     * An optional colon-separated key that specifies extra services to use.
     */
    public static final String SERVICES = NS + "services";

    /**
     * An optional colon-separated key that specifies extra managers to use.
     * This must contain the same number of classes as
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
    public static final String MANAGERS = NS + "managers";

    /**
     * An optional property to specify that no <code>Service</code>s should
     * be configured past the one specified. This may only be used if the
     * <code>AppListener</code> has the value <code>APP_LISTENER_NONE</code>.
     * Valid values for this property are specified by
     * <code>StandardService</code>.
     */
    public static final String FINAL_SERVICE = NS + "finalService";

    /**
     * An enumeration of the known, standard <code>Service</code>s. The
     * ordering represents the order in which the services are configured.
     */
    public enum StandardService {
        /** Enumeration for the Data Service. */
        DataService,
        /** Enumeration for the Watchdog Service. */
        WatchdogService,
        /** Enumeration for the Node Mapping Service. */
        NodeMappingService,
        /** Enumeration for the Task Service. */
        TaskService,
        /** Enumeration for the Client Session Service. */
        ClientSessionService,
        /** Enumeration for the Channel Service. */
        ChannelService;

        /** The last service that gets configured for an application. */
        public static final StandardService LAST_SERVICE = ChannelService;
    }

    /**
     * An optional colon-separated key that specifies which
     * <code>IdentityAuthenticator</code>s to use for the application. The
     * order here is used to define order of precedence when authenticating
     * an identity.
     */
    public static final String AUTHENTICATORS = NS + "app.authenticators";

    /**
     * An optional property that specifies a core server node should be started
     * in the default configuration, where the servers associated with 
     * individual Darkstar services are started on a single physical machine
     * using their default communication ports.  Setting this property to 
     * {@code true} is equivalent to setting the following properties:
     * <ul>
     * <li> {@value #APP_LISTENER} set to {@link #APP_LISTENER_NONE} whose value
     *      is {@value #APP_LISTENER_NONE} to indicate that no application code
     *      will run on the server node
     * <li> {@value #FINAL_SERVICE} set to {@code NodeMappingService} to indicate
     *      the set of services to run on the server node
     * <li> {@value #SERVER_START} set to {@code true} to indicate that the
     *       services' servers should be started
     * <li> {@code com.sun.sgs.impl.service.data.DataServiceImpl.data.store.class}
     *   set to {@code com.sun.sgs.impl.service.data.store.net.DataStoreClient}
     *   to indicate the multi-node data service should be used
     *
     * </ul>
     * 
     */
    public static final String DEFAULT_CORE_SERVER = NS + "default.core.server";
    
    /**
     * An optional property that specifies the default for whether to start the
     * servers associated with services.
     */
    public static final String SERVER_START = NS + "server.start";

    /**
     * An optional property that specifies the default for the name of the host
     * running the servers associated with services.
     */
    public static final String SERVER_HOST = NS + "server.host";
}
