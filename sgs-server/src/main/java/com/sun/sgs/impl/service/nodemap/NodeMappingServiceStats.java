/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
