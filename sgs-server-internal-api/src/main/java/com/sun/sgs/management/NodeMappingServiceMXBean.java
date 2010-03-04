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

import com.sun.sgs.service.NodeMappingService;

/**
 * The management interface for the node mapping service.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface NodeMappingServiceMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs.service:type=NodeMappingService";
    
    // Maybe add number of active identities on this node? 
    //   (don't know how to capture that)
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#addNodeMappingListener addNodeMappingListener}
     * has been called.
     * 
     * @return the number of times {@code addNodeMappingListener} 
     *         has been called
     */
    long getAddNodeMappingListenerCalls();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#addIdentityRelocationListener 
     *  addIdentityRelocationListener} has been called.
     * 
     * @return the number of times {@code addIdentityRelocationListener} 
     *         has been called
     */
    long getAddIdentityRelocationListenerCalls();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#assignNode assignNode} has been called.
     * 
     * @return the number of times {@code assignNode} has been called
     */
    long getAssignNodeCalls();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#getIdentities getIdentities} has been called.
     * 
     * @return the number of times {@code getIdentities} has been called
     */
    long getGetIdentitiesCalls();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#getNode getNode} has been called.
     * @return the number of times {@code getNode} has been called
     */
    long getGetNodeCalls();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#setStatus setStatus} has been called.
     * 
     * @return the number of times {@code setStatus} has been called
     */
    long getSetStatusCalls();
}
