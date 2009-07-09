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

import com.sun.sgs.auth.Identity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of an affinity group.
 */
public class AffinityGroupImpl implements AffinityGroup {

    private final NodeMappingServerImpl server;
    private final long agid;
    private final Identity[] identities;
    private final long[] nodes;
    private long targetNodeId;

    /**
     * Construct an affinity group. When a group is constructed the node
     * membership will be checked and the target node for this group will be
     * the node which most members reside on. Then an attempt will be made to
     * move any identity not on the target node to that node.
     *
     * @param server the node mapping server instance
     * @param agid the id of this affinity group
     * @param identities the members of this group
     * @param nodes the node ids of each of the members
     */
    AffinityGroupImpl(NodeMappingServerImpl server,
                      long agid, Identity[] identities, long[] nodes)
    {
        this.server = server;
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
            server.moveIdentities(stragglers.iterator(), null, targetNodeId);
        }
    }

    @Override
    public long getId() {
        return agid;
    }

    @Override
    public void setNode(long nodeId) {
        if (nodeId != targetNodeId) {
            targetNodeId = nodeId;
            moveStragglers();
        }
    }

    @Override
    public Identity[] getIdentities() {
        return identities;
    }
}
