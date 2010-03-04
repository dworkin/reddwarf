/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.management;

import com.sun.sgs.kernel.NodeType;

/**
 * The immutable management information containing this node's configuration.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface ConfigMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=Config";   
    
    /**
     * Returns the type of this node.
     * 
     * @return the node type
     * 
     */
    NodeType getNodeType();

    /**
     * Returns the application name.
     * @return the application name
     */
    String getAppName();

    /**
     * Returns the application root directory name.
     * 
     * @return the application root directory name
     */
    String getAppRoot();

    /**
     * Returns the application listener class name.
     * 
     * @return the class name of the application listener
     */
    String getAppListener();

    /**
     * Returns the host this node is running on.
     *
     * @return the host this node is running on
     */
    String getHostName();
    
    /** 
     * Returns the server host property.
     * 
     * @return the server host property, which is valid for application nodes
     *         only
     */
    String getServerHostName();

    /**
     * Returns the JMX remote listening port.
     * 
     * @return the port JMX is listening on for remote connections, or 
     *         {@code -1} if no remote JMX connections are supported
     */
    int getJmxPort();
    
    /**
     * Returns the standard transaction timeout, in milliseconds.
     * 
     * @return the standard transaction timeout, in milliseconds
     */
    long getStandardTxnTimeout();

    /**
     * Returns the protocol descriptor, as a {@code String}.
     *
     * @return the protocol descriptor, as a {@code String}
     */
    String getProtocolDescriptor();
}
