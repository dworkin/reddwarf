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
import com.sun.sgs.impl.service.nodemap.GroupCoordinator;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Implementation of an affinity group.
 */
public class AffinityGroupCoordinator implements GroupCoordinator {

    private final NodeMappingServerImpl server;
    private final GroupFinder finder;
    
    // Map nodeID -> groupSet
    private final Map<Long, NavigableSet<Group>> groups =
                                       new HashMap<Long, NavigableSet<Group>>();
    
    /**
     * Affinity group. When a group is constructed the node
     * membership will be checked and the target node for this group will be
     * the node which most members reside on. Then an attempt will be made to
     * move any identity not on the target node to that node.
     */
    private class Group implements Comparable {

        private final long agid;
        private final Identity[] identities;
        private final long[] nodes;
        private long targetNodeId;

        Group(long agid, Identity[] identities, long[] nodes) {
            this.agid = agid;
            this.identities = identities;
            this.nodes = nodes;

            // Find the node that most identites belong to, and move any
            // stragglers to that node. TODO - better way to do this???
            HashMap<Long, Integer> foo = new HashMap<Long, Integer>(nodes.length);
            for (long nodeId : nodes) {
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
            moveStragglers();
        }

        private void moveStragglers() {
            Set<Identity> stragglers = new HashSet<Identity>();
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] != targetNodeId) {
                    stragglers.add(identities[i]);
                }
            }
            System.out.println("Found " + stragglers.size() + " stragglers");
            if (!stragglers.isEmpty()) {
                server.moveIdentities(stragglers, null, targetNodeId);
            }
        }

        void setNode(long nodeId) {
            if (nodeId != targetNodeId) {
                targetNodeId = nodeId;
                moveStragglers();
            }
        }

        @Override
        public int compareTo(Object obj) {
            if (obj == null) {
                throw new NullPointerException();
            }
            if (this.equals(obj)) return 0;

            return identities.length < ((Group)obj).identities.length ? -1 : 1;
        }

        public String toString() {
            return "Group: " + agid + " targetNodeId: " + targetNodeId +
                   " #identities: " + identities.length;
        }
    }

    /**
     * Construct an affinity group coordinator.
     *
     * @param server the node mapping server instance
     * @param agid the id of this affinity group
     * @param identities the members of this group
     * @param nodes the node ids of each of the members
     */
    AffinityGroupCoordinator(Properties properties,
                             NodeMappingServerImpl server,
                             DataService dataService)
    {
        this.server = server;

        // TODO - set by property
        finder = new LabelPropGroupFinder(properties, this);
    }

    @Override
    public synchronized void offload(Node oldNode, long newNodeId) {
        if (oldNode == null) {
            throw new NullPointerException("oldNode can not be null");
        }
        if (newNodeId <= 0) {
            throw new IllegalArgumentException("invalid node id");
        }

        NavigableSet<Group> groupSet = groups.get(oldNode.getId());

        // No groups on old node, exit
        if (groupSet == null) return;

        Group group = groupSet.pollFirst();

        // Empty group set (?), exit
        if (group == null) return;

        System.out.println("moving " + group);

        // Re-target the group and re-insert into groups map
        group.setNode(newNodeId);
        newGroup(group);
    }

    @Override
    public void shutdown() {
        finder.shutdown();
    }

    /**
     * Coordinate a new set of groups. The old set is discarded. This method
     * is called by the finder.
     */
    synchronized void newGroups(/* args???*/) {
        groups.clear();
    }

    private void newGroup(Group group) {
        NavigableSet<Group> groupSet = groups.get(group.targetNodeId);
        if (groupSet == null) {
            groupSet = new TreeSet<Group>();
            groups.put(group.targetNodeId, groupSet);
        }
        System.out.println("adding " + group);
        groupSet.add(group);
    }
}
