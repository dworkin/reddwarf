/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.management;

/**
 * The immutable management information for this node's configuration.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #CONFIG_MXBEAN_NAME}.
 * 
 */
public interface ConfigMXBean {
    /** The name for uniquely identifying this MBean. */
    String CONFIG_MXBEAN_NAME = "com.sun.sgs:type=Config";
    
    // Maybe combine this object with the NodeInfo data?
    
    /**
     * Return the type of this node, one of {@code singleNode}, 
     * {@code coreServerNode}, or {@code appNode}.
     * 
     * @return the node type
     * 
     */
    String getNodeType();

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
     * Returns the TCP port for client connections.
     * FIXME:  update for new protocol
     * 
     * @return the TCP port for application client connections
     */
    int getAppPort();
   
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
    int getJMXPort();
    
    /**
     * Returns the transaction timeout, in milliseconds.
     * 
     * @return the transaction timeout, in milliseconds
     */
    long getTxnTimeout();
}
