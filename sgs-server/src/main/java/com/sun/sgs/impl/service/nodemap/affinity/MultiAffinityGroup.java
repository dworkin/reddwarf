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

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Affinity group.  Key ideas swiped from Keith's branch to help us with
 * merging.
 * <p>
 * These affinity groups can span multiple nodes.
 */
public class MultiAffinityGroup implements AffinityGroup {
    // The group id
    private final long agid;
    // Map Identity -> nodeId
    private final Map<Identity, Long> identities;
    // The node id that most Identities seem to be on
    private final long targetNodeId;

    /**
     * Creates a new affinity group which contains node information
     * @param agid the group id
     * @param identities the identities and their nodes in the group
     */
    MultiAffinityGroup(long agid, Map<Identity, Long> identities) {
        assert identities.size() > 0;
        this.agid = agid;
        this.identities = identities;
        targetNodeId = calcMostUsedNode();
    }

    /**
     * Calculate the node that the most number of identities is on.
     * @return the node id of the most used node
     */
    private long calcMostUsedNode() {
        Map<Long, Integer> nodeCountMap = new HashMap<Long, Integer>();
        for (Long nodeId : identities.values()) {
            Integer count = nodeCountMap.get(nodeId);
            if (count == null) {
                count = new Integer(0);
            }
            count++;
            nodeCountMap.put(nodeId, count);
        }
        long retNode = -1;
        int highestCount = -1;
        for (Entry<Long, Integer> entry : nodeCountMap.entrySet()) {
            int count = entry.getValue();
            if (highestCount > count) {
                highestCount = count;
                retNode = entry.getKey();
            }         
        }
        return retNode;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "AffinityGroup: " + agid + " targetNodeId: " + targetNodeId +
               " #identities: " + identities.size();
    }

    /** {@inheritDoc} */
    public long getId() {
        return agid;
    }

    /** {@inheritDoc} */
    public Set<Identity> getIdentities() {
        return Collections.unmodifiableSet(identities.keySet());
    }

}
