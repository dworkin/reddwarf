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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.management.NodeMappingServiceMXBean;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileOperation;

/**
 * The Statistics MBean object for the node mapping service.
 * 
 */
class NodeMappingServiceStats implements NodeMappingServiceMXBean {

    final AggregateProfileOperation addNodeMappingListenerOp;
    final AggregateProfileOperation addIdentityRelocationListenerOp;
    final ProfileOperation assignNodeOp;
    final ProfileOperation getIdentitiesOp;
    final ProfileOperation getNodeOp;
    final ProfileOperation setStatusOp;
    
    NodeMappingServiceStats(ProfileCollector collector) {
        ProfileConsumer consumer =
            collector.getConsumer(ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                                  "NodeMappingService");

        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;

        addNodeMappingListenerOp = (AggregateProfileOperation)
            consumer.createOperation("addNodeMappingListener", type, level);
        addIdentityRelocationListenerOp = (AggregateProfileOperation)
            consumer.createOperation("addIdentityRelocationListener", 
                                     type, level);
        assignNodeOp =
            consumer.createOperation("assignNode", type, level);
        getIdentitiesOp = 
            consumer.createOperation("getIdentities", type, level);
        getNodeOp =
            consumer.createOperation("getNode", type, level);
        setStatusOp =
            consumer.createOperation("setStatus", type, level);
    }
    
    /** {@inheritDoc} */
    public long getAddNodeMappingListenerCalls() {
        return addNodeMappingListenerOp.getCount();
    }
    
    /** {@inheritDoc} */
    public long getAddIdentityRelocationListenerCalls() {
        return addIdentityRelocationListenerOp.getCount();
    }

    /** {@inheritDoc} */
    public long getAssignNodeCalls() {
        return ((AggregateProfileOperation) assignNodeOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetIdentitiesCalls() {
        return ((AggregateProfileOperation) getIdentitiesOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetNodeCalls() {
        return ((AggregateProfileOperation) getNodeOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSetStatusCalls() {
        return ((AggregateProfileOperation) setStatusOp).getCount();
    }

}
