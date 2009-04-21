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

    final ProfileOperation registerSessionDisconnectListenerOp;
    final ProfileOperation getSessionProtocolOp;
    
    ClientSessionServiceStats(ProfileCollector collector) {
        ProfileConsumer consumer = 
            collector.getConsumer(ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                                  "ClientSessionService");
        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;
        
        registerSessionDisconnectListenerOp =
            consumer.createOperation("registerSessionDisconnectListener", 
                                     type, level);
        getSessionProtocolOp =
            consumer.createOperation("getSessionProtocol", type, level);
    }

    /** {@inheritDoc} */
    public long getRegisterSessionDisconnectListenerCalls() {
        return ((AggregateProfileOperation) 
                    registerSessionDisconnectListenerOp).getCount();
    }

    /** {@inheritDoc} */
    public long getGetSessionProtocolCalls() {
        return ((AggregateProfileOperation) 
                    getSessionProtocolOp).getCount();
    }
}
