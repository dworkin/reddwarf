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

import javax.management.Notification;

/**
 * The management interface for all the nodes in the system, available
 *  only from the core server node.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * <p>
 * Each time a node joins the cluster or fails, a notification of class
 * {@link Notification} is emitted.  The notification will be of type
 * {@value #NODE_STARTED_NOTIFICATION} or {@value #NODE_FAILED_NOTIFICATION}.
 * 
 */
public interface NodesMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=Nodes";
    
    /** The type for node started notifications. */
    String NODE_STARTED_NOTIFICATION = "com.sun.sgs.node.started";
    
    /** The type for node failed notifications. */
    String NODE_FAILED_NOTIFICATION = "com.sun.sgs.node.failed";
    
    // Maybe add method to shut down node?  Need the node shutdown
    // work to tell the watchdog we need the node shut down.
    
    /** 
     * Returns information about the nodes in the system.
     * @return information about the nodes in the system
     */
    NodeInfo[] getNodes();
}
