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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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
