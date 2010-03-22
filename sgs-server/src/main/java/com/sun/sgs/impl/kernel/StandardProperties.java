/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.NodeType;

/**
 * This class contains the standard property keys that the kernel looks for
 * (and may provide) on startup.
 * <p>
 * When when the kernel starts, it is given an application
 * <code>Properties</code> file. Any of the property keys identified here will
 * set the associated behavior for that application. If no value is provided
 * for a given key, then the default or system-provided value is used.
 * Note that some keys are required to have values, specifically
 * <code>APP_NAME</code>, <code>APP_ROOT</code> and <code>APP_LISTENER</code>.
 * <p>
 * Default values can be provided for all applications by using any of the
 * properties specified in this class as a system property.
 */
public final class StandardProperties {
    
    /**
     * This class should not be instantiated
     */
    private StandardProperties() {
        
    }

    // the root of all the Darkstar properties
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
     * An optional colon-separated key that specifies additional services to
     * use. Services will be started in the order that they are specified in
     * this list.
     */
    public static final String SERVICES = NS + "services";

    /**
     * An optional colon-separated key that specifies additional managers to
     * use.  This must contain the same number of classes as
     * <code>SERVICES</code>. Each manager in this list will be paired with
     * the corresponding <code>Service</code> from the <code>SERVICES</code>
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
     * An optional colon-separated key that specifies which node types each
     * configured additional <code>Service</code>/<code>Manager</code> pair
     * should be started on.  Each
     * item in this list must be set to a value in {@link ServiceNodeTypes} and
     * is associated with the <code>Service</code>/<code>Manager</code> at the
     * same respective index in the <code>SERVICES</code> and
     * <code>MANAGERS</code> lists.  If this property is omitted, all
     * configured additional services will default to
     * {@link ServiceNodeTypes#ALL}.
     */
    public static final String SERVICE_NODE_TYPES = NS + "services.node.types";

    /**
     * An enumeration of the possible values to assign in the
     * <code>SERVICE_NODE_TYPES</code> list.  Each value represents which node
     * types a <code>Service</code>/<code>Manager</code> pair will be started
     * on.
     */
    public enum ServiceNodeTypes {
        /**
         * Service should be started on a {@link NodeType#singleNode} only.
         */
        SINGLE,
        /**
         * Service should be started on a {@link NodeType#coreServerNode} only.
         */
        CORE,
        /**
         * Service should be started on an {@link NodeType#appNode} only.
         */
        APP,
        /**
         * Service should be started on either a {@code singleNode} or a
         * {@code coreServerNode}.
         */
        SINGLE_OR_CORE,
        /**
         * Service should be started on either a {@code singleNode} or an
         * {@code appNode}.
         */
        SINGLE_OR_APP,
        /**
         * Service should be started on either a {@code coreServerNode} or an
         * {@code appNode}.
         */
        CORE_OR_APP,
        /**
         * Service should be started on any node type.
         */
        ALL;

        /**
         * Returns {@code true} if this node types identifier indicates that
         * the associated service should be started on the specified node type.
         *
         * @param nodeType the current {@code NodeType}
         * @return {@code true} if the service should be started on the given
         *         node type.
         */
        public boolean shouldStart(NodeType nodeType) {
            switch(nodeType) {
                case singleNode:
                    return equals(SINGLE) ||
                           equals(SINGLE_OR_CORE) ||
                           equals(SINGLE_OR_APP) ||
                           equals(ALL);
                case coreServerNode:
                    return equals(CORE) ||
                           equals(SINGLE_OR_CORE) ||
                           equals(CORE_OR_APP) ||
                           equals(ALL);
                case appNode:
                    return equals(APP) ||
                           equals(SINGLE_OR_APP) ||
                           equals(CORE_OR_APP) ||
                           equals(ALL);
                default:
                    return false;
            }
        }
    }

    /**
     * An enumeration of the known, standard {@code Service}s. The
     * ordering represents the order in which the services are started.
     * Each {@link NodeType} will start up each service in the order
     * specified up to and including the last service configured for that
     * particular {@code NodeType}.  The last service is respectively specified
     * for each node with the {@code LAST_APP_SERVICE},
     * {@code LAST_SINGLE_SERVICE}, and {@code LAST_CORE_SERVICE} attributes.
     * Any additional configured services will then be started after this
     * last service.  Care should therefore be taken for additional services to
     * only depend on standard services that are started on a particular
     * {@code NodeType}.
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

        /** The last service that gets configured for an {@code appNode}. */
        public static final StandardService LAST_APP_SERVICE = ChannelService;

        /** The last service that gets configured for a {@code singleNode}. */
        public static final StandardService LAST_SINGLE_SERVICE =
                                            ChannelService;

        /**
         * The last service that gets configured for a
         * {@code coreServerNode}.
         */
        public static final StandardService LAST_CORE_SERVICE = TaskService;
    }

    /**
     * An optional colon-separated key that specifies which
     * <code>IdentityAuthenticator</code>s to use for the application. The
     * order here is used to define order of precedence when authenticating
     * an identity.
     */
    public static final String AUTHENTICATORS = NS + "app.authenticators";
    
    /**
     *  An optional property that specifies the type of node being started.
     *  It must be set to a value in {@link NodeType} and defaults to
     *  {@code singleNode}.  The value of this property may cause other
     *  property settings to be overridden.
     *  <p>
     *  In particular, setting this property to {@code coreServerNode} causes
     *  the following property to be set:
     * <ul>
     * <li>
     *   {@code com.sun.sgs.impl.service.data.DataServiceImpl.data.store.class}
     *   set to {@code com.sun.sgs.impl.service.data.store.net.DataStoreClient}
     *   to indicate the multi-node data service should be used
     *
     * </ul>
     * <p>
     * The unit tests rely on the default not modifying any other properties.
     */
    public static final String NODE_TYPE = NS + "node.type";
    
    /**
     * An optional property that specifies the default for the name of the host
     * running the servers associated with services.
     */
    public static final String SERVER_HOST = NS + "server.host";
    
    /**
     * An optional system property (this is not a Darkstar property) which
     * enables remote JMX monitoring, and specifies the port JMX is listening
     * on.
     */
    public static final String SYSTEM_JMX_REMOTE_PORT =
        "com.sun.management.jmxremote.port";

    /**
     * The property for specifying the maximum length of time, in
     * milliseconds, for a client session to relocate to a new node.
     */
    public static final String SESSION_RELOCATION_TIMEOUT_PROPERTY =
	"com.sun.sgs.impl.service.session.relocation.timeout";

    /**
     * The default session relocation timeout, in milliseconds.
     */
    public static final long DEFAULT_SESSION_RELOCATION_TIMEOUT = 10000;
}
