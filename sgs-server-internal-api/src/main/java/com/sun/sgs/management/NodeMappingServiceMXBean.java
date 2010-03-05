/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
