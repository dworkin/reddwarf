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

import com.sun.sgs.service.NodeMappingService;

/**
 * The management interface for the node mapping service.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #NODEMAP_SERVICE_MXBEAN_NAME}.
 * 
 */
public interface NodeMappingServiceMXBean {

    /** The name for uniquely identifying this MBean. */
    String NODEMAP_SERVICE_MXBEAN_NAME = 
                                "com.sun.sgs.service:type=NodeMappingService";
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#addNodeMappingListener(NodeMappingListener) 
     * addNodeMappingListener} has been called.
     * @return the number of times {@code addNodeMappingListener} 
     *         has been called
     */
    long getAddNodeMappingListenerCount();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#assignNode(Class, Identity) assignNode} 
     * has been called.
     * @return the number of times {@code assignNode} has been called
     */
    long getAssignNodeCount();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#getIdentities(long) getIdentities} 
     * has been called.
     * @return the number of times {@code getIdentities} has been called
     */
    long getGetIdentitiesCount();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#getNode(Identity) getNode} has been called.
     * @return the number of times {@code getNode} has been called
     */
    long getGetNodeCount();
    
    /**
     * Returns the number of times 
     * {@link NodeMappingService#setStatus(Class, Identity, boolean) 
     * setStatus} has been called.
     * @return the number of times {@code setStatus} has been called
     */
    long getSetStatusCount();
}
