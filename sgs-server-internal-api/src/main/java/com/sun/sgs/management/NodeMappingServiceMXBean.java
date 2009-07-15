/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
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
