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

import com.sun.sgs.impl.service.nodemap.GroupCoordinator;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Implementation of an group coordinator. This coordinator manages groups
 * composed of identities having some affinity with the other members of the
 * group.
 */
public class AffinityGroupCoordinator implements GroupCoordinator {

    private final NodeMappingServerImpl server;
    private final GroupFinder finder;
    
    // Map nodeID -> groupSet
    private final Map<Long, NavigableSet<AffinityGroup>> groups =
                               new HashMap<Long, NavigableSet<AffinityGroup>>();

    /**
     * Construct an affinity group coordinator.
     */
    public AffinityGroupCoordinator(Properties properties,
                                    NodeMappingServerImpl server,
                                    ComponentRegistry systemRegistry,
                                    TransactionProxy txnProxy)
        throws Exception
    {
        this.server = server;

        System.out.println("*** constructing AffinityGroupCoordinator ***");

        // TODO - set by property
        finder = new UserGroupFinderServerImpl(properties, this,
                                               systemRegistry, txnProxy);
    }

    @Override
    public void start() {
        finder.start();
    }

    @Override
    public void stop() {
        finder.stop();
    }

    @Override
    public synchronized void offload(Node oldNode, long newNodeId) {
        if (oldNode == null) {
            throw new NullPointerException("oldNode can not be null");
        }
        if (newNodeId <= 0) {
            throw new IllegalArgumentException("invalid node id");
        }

        NavigableSet<AffinityGroup> groupSet = groups.get(oldNode.getId());

        // No groups on old node, exit
        if (groupSet == null) return;

        AffinityGroup group = groupSet.pollFirst();

        // Empty group set (?), exit
        if (group == null) return;

        System.out.println("moving " + group);

        // Re-target the group and re-insert into groups map
        group.setTargetNode(newNodeId, server);
        newGroup(group);
    }

    @Override
    public void shutdown() {
        finder.shutdown();
    }

    /**
     * Coordinate a new set of groups. The old set is discarded. This method
     * is called by the finder.
     *
     * TODO - synchronization right? or do it better
     */
    synchronized void newGroups(Collection<AffinityGroup> newGroups) {
        groups.clear();
        for (AffinityGroup group : newGroups) {
            newGroup(group);
        }
    }

    synchronized private void newGroup(AffinityGroup group) {
        long targetNodeId = group.setTargetNode(server);
        NavigableSet<AffinityGroup> groupSet = groups.get(targetNodeId);
        if (groupSet == null) {
            groupSet = new TreeSet<AffinityGroup>();
            groups.put(targetNodeId, groupSet);
        }
        System.out.println("adding " + group);
        groupSet.add(group);
    }
}
