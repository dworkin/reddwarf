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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.management.NodeMappingServiceMXBean;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.service.ProfileService;

/**
 * The Statistics MBean object for the node mapping service.
 * 
 */
public class NodeMappingServiceStats implements NodeMappingServiceMXBean {

    final ProfileOperation addNodeMappingListenerOp;
    final ProfileOperation assignNodeOp;
    final ProfileOperation getIdentitiesOp;
    final ProfileOperation getNodeOp;
    final ProfileOperation setStatusOp;
    
    NodeMappingServiceStats(ProfileService profileService, String name) {
        ProfileConsumer consumer =
            profileService.getProfileCollector().createConsumer(name);

        ProfileLevel level = ProfileLevel.MAX;
        addNodeMappingListenerOp =
            consumer.registerOperation("addNodeMappingListener", false, level);
        assignNodeOp =
            consumer.registerOperation("assignNode", false, level);
        getIdentitiesOp = 
            consumer.registerOperation("getIdentities", true, level);
        getNodeOp =
            consumer.registerOperation("getNode", true, level);
        setStatusOp =
            consumer.registerOperation("schedulePeriodicTask", false, level);
    }
    
    /** {@inheritDoc} */
    public long getAddNodeMappingListenerCount() {
        return addNodeMappingListenerOp.getCount();
    }

    /** {@inheritDoc} */
    public long getAssignNodeCount() {
        return assignNodeOp.getCount();
    }

    /** {@inheritDoc} */
    public long getGetIdentitiesCount() {
        return getIdentitiesOp.getCount();
    }

    /** {@inheritDoc} */
    public long getGetNodeCount() {
        return getNodeOp.getCount();
    }

    /** {@inheritDoc} */
    public long getSetStatusCount() {
        return setStatusOp.getCount();
    }

}
