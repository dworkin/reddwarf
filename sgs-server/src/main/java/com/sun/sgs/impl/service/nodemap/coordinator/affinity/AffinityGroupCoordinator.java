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
import com.sun.sgs.service.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.management.GroupCoordinatorMXBean;
import com.sun.sgs.management.GroupCoordinatorMXBean.GroupInfo;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * Implementation of an group coordinator. This coordinator manages groups
 * composed of identities having some affinity with the other members of the
 * group.
 */
public class AffinityGroupCoordinator implements GroupCoordinator {

    /** Package name for this class. */
    private static final String PKG_NAME =
                                    "com.sun.sgs.impl.service.nodemap.affinity";

    private static final String FINDER_CLASS_PROPERTY =
            PKG_NAME + ".finder.class";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME + ".coordinator"));

    private final NodeMappingServerImpl server;
    private final GroupFinder finder;
    
    // Map nodeID -> groupSet where groupSet is groupId -> group
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
        logger.log(Level.CONFIG, "Creating AffinityGroupCoordinator");

        this.server = server;

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        if (wrappedProps.getProperty(FINDER_CLASS_PROPERTY) == null) {
            finder = new UserGroupFinderServerImpl(properties, this,
                                                   systemRegistry, txnProxy);
        } else {
            finder = wrappedProps.getClassInstanceProperty(
                                FINDER_CLASS_PROPERTY, GroupFinder.class,
                                new Class[] { Properties.class,
                                              AffinityGroupCoordinator.class,
                                              ComponentRegistry.class,
                                              TransactionProxy.class },
                                properties, this, systemRegistry, txnProxy);
        }
        logger.log(Level.CONFIG, "Group finder: {0}",
                   finder.getClass().getName());

        
        // create our profiling info and register our MBean
        ProfileCollector collector =
            systemRegistry.getComponent(ProfileCollector.class);
        try {
            collector.registerMBean(new AffinityGroupCoordinatorMXBean(),
                                    GroupCoordinatorMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
    }

    @Override
    public void start() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "starting coordinator");
        }
        finder.start();
    }

    @Override
    public void stop() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "stopping coordinator");
        }
        finder.stop();
    }

    // TODO - do a better job with locking
    @Override
    public synchronized void offload(Node oldNode)
        throws NoNodesAvailableException
    {
        if (oldNode == null) {
            throw new NullPointerException("oldNode can not be null");
        }

        NavigableSet<AffinityGroup> groupSet = groups.get(oldNode.getId());

        // No groups on old node, exit
        if (groupSet == null) return;

        for (AffinityGroup group : groupSet) {

            long newNodeId = server.chooseNode();

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "moving group {0} to node {1}",
                           group, newNodeId);
            }

            // Re-target the group and re-insert into groups map
            group.setTargetNode(newNodeId, server);
            newGroup(group);
            
            // If the node is alive, then just move off one group
            if (oldNode.isAlive()) {
                break;
            }
        }
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
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "received {0} new groups",newGroups.size());
        }
        groups.clear();
        for (AffinityGroup group : newGroups) {
            newGroup(group);
        }
    }

    private void newGroup(AffinityGroup group) {

        // The findTargetNode call may cause client sessions to move
        long targetNodeId = group.findTargetNode(server);
        NavigableSet<AffinityGroup> groupSet = groups.get(targetNodeId);
        if (groupSet == null) {
            groupSet = new TreeSet<AffinityGroup>();
            groups.put(targetNodeId, groupSet);
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "adding {0}", group);
        }
        groupSet.add(group);
    }

    private class AffinityGroupCoordinatorMXBean
        implements GroupCoordinatorMXBean
    {

        @Override
        public int getNumGroups() {
            return groups.size();
        }

        @Override
        public List<GroupInfo> getGroups(long nodeId) {
            NavigableSet<AffinityGroup> groupSet = groups.get(nodeId);

            if (groupSet == null) return null;

            List<GroupInfo> groupInfoList =
                                     new ArrayList<GroupInfo>(groupSet.size());

            // TODO CME potential here
            for (AffinityGroup group : groupSet) {
                groupInfoList.add(group.getGroupInfo());
            }
            return groupInfoList;
        }

        @Override
        public boolean isRunning() {
            return true;    // todo
        }

        @Override
        public void start() {
            AffinityGroupCoordinator.this.start();
        }

        @Override
        public void stop() {
            AffinityGroupCoordinator.this.stop();
        }
    }
}
