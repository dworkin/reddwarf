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
import com.sun.sgs.management.GroupCoordinatorMXBean.GroupInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    // Map Identity -> nodeId
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

    // TODO - this only works if add() is not called once this object has been
    // placed in a map. Really should re-work this so that the identities are
    // assembled elsewhere and passed to this constructor.
    @Override
    public int compareTo(Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        if (this.equals(obj)) return 0;

        // Sorting is based on the number of identities. (size of group)
        return identities.size() <
                              ((AffinityGroup)obj).identities.size() ? -1 : 1;
    }

    @Override
    public String toString() {
        return "AffinityGroup: " + agid + " targetNodeId: " + targetNodeId +
               " #identities: " + identities.size();
    }

    /**
     * Get a group info object for this group.
     *
     * @return a group info object for this group
     */
    GroupInfo getGroupInfo() {
        return new GroupInfoImpl(agid, targetNodeId, identities.keySet());
    }

    /**
     * Wrapper class for group information.
     */
    private static class GroupInfoImpl implements GroupInfo {

        private final long groupId;
        private final long nodeId;
        private final List<String> idNames;

        GroupInfoImpl(long groupId, long nodeId, Set<Identity> identities) {
            this.groupId = groupId;
            this.nodeId = nodeId;
            this.idNames = new ArrayList<String>(identities.size());

            // TODO - CME potential
            for (Identity identity : identities) {
                idNames.add(identity.getName());
            }
        }

        @Override
        public long getGroupId() {
            return groupId;
        }

        @Override
        public long getTargetNodeId() {
            return nodeId;
        }

        @Override
        public List<String> getIdentities() {
            return idNames;
        }
    }
}
