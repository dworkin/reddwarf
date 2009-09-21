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
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.management.NodeInfo;
import com.sun.sgs.management.WatchdogServiceMXBean;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileOperation;

/**
 *
 * The Statistics MBean object for the watchdog service.
 */
class WatchdogServiceStats implements WatchdogServiceMXBean {
    // the backing watchdog service
    final WatchdogServiceImpl watchdog;
    
    // the profiled operations
    final ProfileOperation addNodeListenerOp;
    final ProfileOperation addRecoveryListenerOp;
    final ProfileOperation getBackupOp;
    final ProfileOperation getNodeOp;
    final ProfileOperation getNodesOp;
    final ProfileOperation isLocalNodeAliveOp;
    final ProfileOperation isLocalNodeAliveNonTransOp;
    
    WatchdogServiceStats(ProfileCollector collector, WatchdogServiceImpl wdog) {
        watchdog = wdog;
        
        ProfileConsumer consumer = 
            collector.getConsumer(ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                                  "WatchdogService");
        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;
        
        addNodeListenerOp =
            consumer.createOperation("addNodeListener", type, level);
        addRecoveryListenerOp =
            consumer.createOperation("addRecoveryListener", type, level);
        getBackupOp = 
            consumer.createOperation("getBackup", type, level);
        getNodeOp =
            consumer.createOperation("getNode", type, level);
        getNodesOp =
            consumer.createOperation("getNodes", type, level);
        isLocalNodeAliveOp =
            consumer.createOperation("isLocalNodeAlive", type, level);
        isLocalNodeAliveNonTransOp =
            consumer.createOperation("isLocalNodeAliveNonTransactional", 
                                     type, level);
    }
    
    /** {@inheritDoc} */
    public long getAddNodeListenerCalls() {
        return ((AggregateProfileOperation) addNodeListenerOp).getCount();
    }
        
    /** {@inheritDoc} */
    public long getAddRecoveryListenerCalls() {
        return ((AggregateProfileOperation) addRecoveryListenerOp).getCount();
    }
        
    /** {@inheritDoc} */
    public long getGetBackupCalls() {
        return ((AggregateProfileOperation) getBackupOp).getCount();
    }
        
    /** {@inheritDoc} */
    public long getGetNodeCalls() {
        return ((AggregateProfileOperation) getNodeOp).getCount();
    }
        
    /** {@inheritDoc} */
    public long getGetNodesCalls() {
        return ((AggregateProfileOperation) getNodesOp).getCount();
    }
        
    /** {@inheritDoc} */
    public long getIsLocalNodeAliveCalls() {
        return ((AggregateProfileOperation) isLocalNodeAliveOp).getCount();
    }
        
    /** {@inheritDoc} */
    public long getIsLocalNodeAliveNonTransactionalCalls() {
        return ((AggregateProfileOperation) isLocalNodeAliveNonTransOp).
                getCount();
    }
    
    /** {@inheritDoc} */
    public NodeInfo getStatusInfo() {
        return watchdog.getNodeStatusInfo();
    }
}
