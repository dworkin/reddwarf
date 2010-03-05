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

import com.sun.sgs.service.Node.Health;
import com.sun.sgs.service.WatchdogService;

/**
 * The management interface for the watchdog service.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface WatchdogServiceMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs.service:type=WatchdogService";

    /**
     * Return the health of the local node.
     *
     * @return the health of the local node.
     */
    Health getNodeHealth();

    /**
     * Set the health of the local node. If the specified health is worse than
     * the node's health, then the node's health is set to the specified health.
     * If the specified health is better than the node's health, the node's
     * health will not change.
     *
     * @param health a node health
     */
    void setNodeHealth(Health health);

    /**
     * Returns the number of times {@link WatchdogService#addNodeListener 
     * addNodeListener} has been called.
     * 
     * @return the number of times {@code addNodeListener} has been called
     */
    long getAddNodeListenerCalls();
        
    /**
     * Returns the number of times {@link WatchdogService#addRecoveryListener 
     * addRecoveryListener} has been called.
     * 
     * @return the number of times {@code addRecoveryListener} has been called
     */
    long getAddRecoveryListenerCalls();
        
    /**
     * Returns the number of times {@link WatchdogService#getBackup 
     * getBackup} has been called.
     * 
     * @return the number of times {@code getBackup} has been called
     */
    long getGetBackupCalls();
        
    /**
     * Returns the number of times {@link WatchdogService#getNode 
     * getNode} has been called.
     * 
     * @return the number of times {@code getNode} has been called
     */
    long getGetNodeCalls();
        
    /**
     * Returns the number of times {@link WatchdogService#getNodes 
     * getNodes} has been called.
     * 
     * @return the number of times {@code getNodes} has been called
     */
    long getGetNodesCalls();

    /**
     * Returns the number of times {@link WatchdogService#getLocalNodeHealth
     * getLocalNodeHealth} has been called.
     *
     * @return the number of times {@code getLocalNodeHealth} has been called
     */
    long getGetLocalNodeHealthCalls();

    /**
     * Returns the number of times
     * {@link WatchdogService#getLocalNodeHealthNonTransactional
     * getLocalNodeHealthNonTransactional} has been called.
     *
     * @return the number of times {@code getLocalNodeHealthNonTransactional}
     *         has been called
     */
    long getGetLocalNodeHealthNonTransactionalCalls();

    /**
     * Returns the number of times {@link WatchdogService#isLocalNodeAlive 
     * isLocalNodeAlive} has been called.
     * 
     * @return the number of times {@code isLocalNodeAlive} has been called
     */
    long getIsLocalNodeAliveCalls();
        
    /**
     * Returns the number of times 
     * {@link WatchdogService#isLocalNodeAliveNonTransactional 
     * isLocalNodeAliveNonTransactional} has been called.
     * 
     * @return the number of times {@code isLocalNodeAliveNonTransactional} 
     *         has been called
     */
    long getIsLocalNodeAliveNonTransactionalCalls();
    
    /**
     * Returns status information about this node.
     * 
     * @return status information about this node
     */
    NodeInfo getStatusInfo();
}
