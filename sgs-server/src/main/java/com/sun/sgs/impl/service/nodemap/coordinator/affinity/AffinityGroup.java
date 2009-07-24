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

package com.sun.sgs.impl.service.nodemap.coordinator.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Affinity group. When a group is constructed the node
 * membership will be checked and the target node for this group will be
 * the node which most members reside on. Then an attempt will be made to
 * move any identity not on the target node to that node.
 * <p>
 * This object implements Comparable and when placed in an sorted collection
 * these objects will be sorted based on the number of identities in the group.
 */
class AffinityGroup implements Comparable {

    private final long agid;
    private final Map<Identity, Long> identities =
                                            new HashMap<Identity, Long>();

    private long targetNodeId = -1;

    AffinityGroup(long agid) {
        this.agid = agid;
    }

    synchronized void add(Identity identity, long nodeId) {
        assert targetNodeId < 0;
        identities.put(identity, nodeId);
    }

    /**
     * Find the node that most identities in this group belong to, and move any
     * stragglers to that node.
     *
     * @param server the node mapping server
     *
     * @return the target node
     */
    long setTargetNode(NodeMappingServerImpl server) {
        // Find the node that most identites belong to, and move any
        // stragglers to that node. TODO - better way to do this???
        HashMap<Long, Integer> foo =
                                  new HashMap<Long, Integer>(identities.size());
        for (long nodeId : identities.values()) {
            Integer count = foo.get(nodeId);
            if (count == null) {
                foo.put(nodeId, 1);
            } else {
                count++;
                foo.put(nodeId, count);
            }
        }
        int max = 0;
        for (Map.Entry<Long, Integer> entry : foo.entrySet()) {
            if (entry.getValue() > max) {
                targetNodeId = entry.getKey();
                max = entry.getValue();
            }
        }
        System.out.println("Found target node to be " + targetNodeId + " with " + max + " count");
        moveStragglers(server);
        return targetNodeId;
    }

    /**
     * Set the target node for this group.
     *
     * @param nodeId a node id
     * @param server the node mapping server
     */
    void setTargetNode(long nodeId, NodeMappingServerImpl server) {
        assert nodeId >= 0;
        if (nodeId != targetNodeId) {
            targetNodeId = nodeId;
            moveStragglers(server);
        }
    }

    private void moveStragglers(NodeMappingServerImpl server) {
        assert targetNodeId >= 0;
        Set<Identity> stragglers = new HashSet<Identity>();
        for (Map.Entry<Identity, Long> entry : identities.entrySet()) {
            if (entry.getValue() != targetNodeId) {
                stragglers.add(entry.getKey());
                entry.setValue(targetNodeId);
            }
        }
        System.out.println("Found " + stragglers.size() + " stragglers");
        if (!stragglers.isEmpty()) {
            server.moveIdentities(stragglers, null, targetNodeId);
        }
    }

    @Override
    public int compareTo(Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        if (this.equals(obj)) return 0;

        // Sorting is based on the number of identities.
        return identities.size() <
                              ((AffinityGroup)obj).identities.size() ? -1 : 1;
    }

    @Override
    public String toString() {
        return "AffinityGroup: " + agid + " targetNodeId: " + targetNodeId +
               " #identities: " + identities.size();
    }
}
