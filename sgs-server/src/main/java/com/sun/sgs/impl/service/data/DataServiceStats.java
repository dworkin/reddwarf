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

package com.sun.sgs.impl.service.data;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.management.DataServiceMXBean;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileOperation;

/**
 * The Statistics MBean object for the data service.
 */
class DataServiceStats implements DataServiceMXBean {

    // the profiled operations
    final ProfileOperation createRefOp;
    final ProfileOperation getBindingOp;
    final ProfileOperation getBindingForUpdateOp;
    final ProfileOperation getObjectIdOp;
    final ProfileOperation markForUpdateOp;
    final ProfileOperation nextBoundNameOp;
    final ProfileOperation removeBindingOp;
    final ProfileOperation removeObjOp;
    final ProfileOperation setBindingOp;
    final ProfileOperation getLocalNodeIdOp;
    final ProfileOperation createRefForIdOp;
    final ProfileOperation getServiceBindingOp;
    final ProfileOperation getServiceBindingForUpdateOp;
    final ProfileOperation nextObjIdOp;
    final ProfileOperation nextServiceBoundNameOp;
    final ProfileOperation removeServiceBindingOp;
    final ProfileOperation setServiceBindingOp;
    
    DataServiceStats(ProfileCollector collector) {
        ProfileConsumer consumer = 
            collector.getConsumer(ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                                  "DataService");
        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;
        
        // Manager operations
        createRefOp =
            consumer.createOperation("createReference", type, level);
        getBindingOp =
            consumer.createOperation("getBinding", type, level);
        getBindingForUpdateOp =
            consumer.createOperation("getBindingForUpdate", type, level);
        getObjectIdOp =
	    consumer.createOperation("getObjectId", type, level);
        markForUpdateOp =
            consumer.createOperation("markForUpdate", type, level);
        nextBoundNameOp =
            consumer.createOperation("nextBoundName", type, level);
        removeBindingOp =
            consumer.createOperation("removeBinding", type, level);
        removeObjOp =
            consumer.createOperation("removeObject", type, level);
        setBindingOp =
            consumer.createOperation("setBinding", type, level);
        // Service operations
        getLocalNodeIdOp =
            consumer.createOperation("getLocalNodeId", type, level);
        createRefForIdOp =
            consumer.createOperation("createReferenceForId", type, level);
        getServiceBindingOp =
            consumer.createOperation("getServiceBinding", type, level);
        getServiceBindingForUpdateOp = consumer.createOperation(
	    "getServiceBindingForUpdate", type, level);
        nextObjIdOp =
            consumer.createOperation("nextObjectId", type, level);
        nextServiceBoundNameOp = 
            consumer.createOperation("nextServiceBoundName", type, level);
        removeServiceBindingOp =
            consumer.createOperation("removeServiceBinding", type, level);
        setServiceBindingOp =
            consumer.createOperation("setServiceBinding", type, level);
    }
    
    /** {@inheritDoc} */
    public long getCreateReferenceCalls() {
        return ((AggregateProfileOperation) createRefOp).getCount();
    }

    /** {@inheritDoc} */
    public long getCreateReferenceForIdCalls() {
       return ((AggregateProfileOperation) createRefForIdOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetBindingCalls() {
        return ((AggregateProfileOperation) getBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetBindingForUpdateCalls() {
        return ((AggregateProfileOperation) getBindingForUpdateOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetLocalNodeIdCalls() {
        return ((AggregateProfileOperation) getLocalNodeIdOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetObjectIdCalls() {
        return ((AggregateProfileOperation) getObjectIdOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetServiceBindingCalls() {
        return ((AggregateProfileOperation) getServiceBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetServiceBindingForUpdateCalls() {
        return ((AggregateProfileOperation)
		getServiceBindingForUpdateOp).getCount();
    }

    /** {@inheritDoc} */
    public long getMarkForUpdateCalls() {
        return ((AggregateProfileOperation) markForUpdateOp).getCount();
    }

    /** {@inheritDoc} */
    public long getNextBoundNameCalls() {
        return ((AggregateProfileOperation) nextBoundNameOp).getCount();
    }

    /** {@inheritDoc} */
    public long getNextObjectIdCalls() {
        return ((AggregateProfileOperation) nextObjIdOp).getCount();
    }

    /** {@inheritDoc} */
    public long getNextServiceBoundNameCalls() {
        return ((AggregateProfileOperation) nextServiceBoundNameOp).getCount();
    }

    /** {@inheritDoc} */
    public long getRemoveBindingCalls() {
        return ((AggregateProfileOperation) removeBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getRemoveObjectCalls() {
        return ((AggregateProfileOperation) removeObjOp).getCount();
    }

    /** {@inheritDoc} */
    public long getRemoveServiceBindingCalls() {
        return ((AggregateProfileOperation) removeServiceBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSetBindingCalls() {
        return ((AggregateProfileOperation) setBindingOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSetServiceBindingCalls() {
        return ((AggregateProfileOperation) setServiceBindingOp).getCount();
    }

}
