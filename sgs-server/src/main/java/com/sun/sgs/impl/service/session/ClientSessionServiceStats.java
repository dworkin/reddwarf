/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.management.ClientSessionServiceMXBean;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileOperation;

/**
 * The Statistics MBean object for the client session service.
 */
class ClientSessionServiceStats implements ClientSessionServiceMXBean {

    final ProfileOperation addSessionStatusListenerOp;
    final ProfileOperation getSessionProtocolOp;
    final ProfileOperation isRelocatingToLocalNodeOp;
    
    ClientSessionServiceStats(ProfileCollector collector) {
        ProfileConsumer consumer = 
            collector.getConsumer(ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                                  "ClientSessionService");
        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;
        
        addSessionStatusListenerOp =
            consumer.createOperation("addSessionStatusListener", 
                                     type, level);
        getSessionProtocolOp =
            consumer.createOperation("getSessionProtocol", type, level);
        isRelocatingToLocalNodeOp =
            consumer.createOperation("isRelocatingToLocalNode", type, level);
    }

    /** {@inheritDoc} */
    public long getAddSessionStatusListenerCalls() {
        return ((AggregateProfileOperation) 
                    addSessionStatusListenerOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetSessionProtocolCalls() {
        return ((AggregateProfileOperation) 
                    getSessionProtocolOp).getCount();
    }

    /** {@inheritDoc} */
    public long getIsRelocatingToLocalNodeCalls() {
        return ((AggregateProfileOperation)
		    isRelocatingToLocalNodeOp).getCount();
    }
}
