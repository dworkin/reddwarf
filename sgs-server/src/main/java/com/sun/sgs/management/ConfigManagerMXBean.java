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
 * The management information for this node's configuration.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #CONFIG_MXBEAN_NAME}.
 * 
 */
public interface ConfigManagerMXBean {
    String CONFIG_MXBEAN_NAME = "com.sun.sgs:type=Config";

    /**
     * Get type of this Darkstar Node
     */
    String getNodeType();

    /**
     * Get application name
     */
    String getAppName();

    /**
     * Get application root directory
     */
    String getAppRoot();

    /**
     * Get class name of appListener
     */
    String getAppListener();

    /**
     * Get TCP port for client connections
     */
    int getAppPort();
   
    /** 
     * Get server host property, valid for application nodes
     */
    String getServerHostName();

    /**
     * Get JMX listening port. A value of {@code -1} means the
     * node is enabled for local monitoring only.
     */
    int getJMXPort();
    
    /**
     * Get transaction timeout, in milliseconds
     */
    long getTxnTimeout();

}
