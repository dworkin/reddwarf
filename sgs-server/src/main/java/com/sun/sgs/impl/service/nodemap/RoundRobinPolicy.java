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
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.WatchdogService;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A very simple round robin assignment policy.
 * * <p>
 * The {@link #RoundRobinPolicy constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.policy.movecount
 *	</b></code><br>
 *	<i>Default:</i> {@code 0}
 *
 * <dd style="padding-top: .5em">This property is for simple testing purposes, 
 *      and specifies how often an identity mapping should be
 *      arbitrarily moved.  If this value is positive, an identity will
 *      be selected from a live node and moved to a different live node every
 *      {@code movecount} calls to {@link #chooseNode}.  If this value is
 *      negative or {@code 0}, no arbitrary movement will take place.  
 *      Specifying a positive value for {@code movecount} is a testing aid. <p>
 * 
 *
 * </dl> <p>
 */
class RoundRobinPolicy implements NodeAssignPolicy {
    /** Package name for this class. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
     /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME + ".policy"));
    private static final String MOVE_COUNT_PROPERTY =
            PKG_NAME + ".policy.movecount";
    private static final int DEFAULT_MOVE_COUNT = 0;
    
    private final NodeMappingServerImpl server;
    private final List<Long> liveNodes = new ArrayList<Long>();
    private final AtomicInteger nextNode = new AtomicInteger();
    
    private final int moveCount;
    /* The number of times chooseNode has been called */
    private int chooseNodeCount = 0;
    /** The next node we'll select to move an identity from */
    private Random nextMoveNode = null;
    
    /** Creates a new instance of RoundRobinPolicy */
    public RoundRobinPolicy(Properties props, NodeMappingServerImpl server) {
        this.server = server;
        
        PropertiesWrapper wrappedProps = new PropertiesWrapper(props);
        moveCount = wrappedProps.getIntProperty(
                MOVE_COUNT_PROPERTY, DEFAULT_MOVE_COUNT);
    }
    
    /** {@inheritDoc} */
    public synchronized long chooseNode(Identity id, long requestingNode) 
        throws NoNodesAvailableException 
    {
        if (liveNodes.size() < 1) {
            // We don't have any live nodes to assign to.
            // Let the caller figure it out.
            throw new NoNodesAvailableException("no live nodes available");
        }  
        
        long chosenNode =
                liveNodes.get(nextNode.getAndIncrement() % liveNodes.size());
        
        if (moveCount > 0) {
            maybeMoveIdentity(); 
        }
        
        return chosenNode;
    }
    
    /** {@inheritDoc} */
    public synchronized void nodeStarted(long nodeId) {
        liveNodes.add(nodeId);
    }

    /** {@inheritDoc} */
    public synchronized void nodeStopped(long nodeId) {
        liveNodes.remove(nodeId);
    }
    
    /** {@inheritDoc} */
    public synchronized void reset() {
        liveNodes.clear();
    }
    
    private synchronized void maybeMoveIdentity() {
        chooseNodeCount++;
        
        if (chooseNodeCount >= moveCount) {    
            // Reset our count
            chooseNodeCount = 0;
            
            // Lazily create our random number generator
            if (nextMoveNode == null) {
                nextMoveNode = new Random();
            }
            
            // Get a random node to try.  If we assign to the same node
            // the identity is already on, we'll try picking an identity
            // on the next node on the list.  We continue trying until
            // we've tried each of the list elements.
            int tryNode = nextMoveNode.nextInt(Integer.MAX_VALUE);
            boolean done = false;
            for (int i = 0; i < liveNodes.size() && !done; i++) {
                long nodeId = liveNodes.get((tryNode + i) % liveNodes.size());
                // Find an identity on node.
                GetIdOnNodeTask task =
                    new GetIdOnNodeTask(server.getDataService(), 
                        server.watchdogService, nodeId, logger);
                try {
                    server.runTransactionally(task);
                    IdentityMO idmo = task.getId();
                    // idmo could be null if no identities are assigned
                    // to the node
                    if (idmo != null) {
                        Identity idToMove = idmo.getIdentity();
                        Node node = task.getNode();
                        try {
                            long newnode =
                                server.mapToNewNode(idToMove, null, node, 
                                                 NodeAssignPolicy.SERVER_NODE);
                            
                            // mapToNewNode will call this method again. We
                            // want to make sure to reset where the next id
                            // will be assigned to, to keep things somewhat
                            // predictable.  
                            // Also, if the node we choose to move an identity
                            // from happens to be the next node this policy will
                            // choose to assign to, we'll never pick a new node
                            // for assignment in this loop (each iteration will
                            // select the next index in liveNodes to move an 
                            // identity from and make the next assignment to).
                            
                            nextNode.getAndDecrement();
                            
                            logger.log(Level.FINEST, "Move Identity attempt: " +
                                    "chose {0} for id {1} on old node {2}",
                                    newnode, idToMove, node);
                            done = newnode != nodeId;
                        } catch (NoNodesAvailableException e) {
                            // Nothing to do here.
                        }

                    }
                    
                } catch (Exception ex) {
                    logger.logThrow(Level.WARNING, ex, 
                          "Unexpected exception while attempting to choose " +
                          "an identity to move from node id {0}" + nodeId);
                }
            } 
        }
    }
    
    /**
     *  Task to support arbitrary identity movement.  Returns the 
     *  first identity on a given nodeId, and looks up the {@code Node}
     *  for the nodeId.
     */
    static class GetIdOnNodeTask extends AbstractKernelRunnable {
        /** The first identity found on the node id */
        private IdentityMO idmo = null;
        /** The node correspondng to the node id */
        private Node node = null;

        private final DataService dataService;
        private final WatchdogService watchdogService;
        private final long nodeId;
        private final String nodekey;
        private final LoggerWrapper logger;
        
        GetIdOnNodeTask(DataService dataService, 
                WatchdogService watchdogService, long nodeId,
                LoggerWrapper logger) 
        {
	    super(null);
            this.dataService = dataService;
            this.watchdogService = watchdogService;
            this.nodeId = nodeId;
            this.nodekey = NodeMapUtil.getPartialNodeKey(nodeId);
            this.logger = logger;
        }
        
        public void run() throws Exception {
            try {
                String key = dataService.nextServiceBoundName(nodekey);
                boolean done = (key == null || !key.contains(nodekey));
                if (!done) {
                    idmo = (IdentityMO) dataService.getServiceBinding(key); 
                }
                node = watchdogService.getNode(nodeId);
            } catch (Exception e) {
                logger.logThrow(Level.WARNING, e, 
                        "Failed to get key or binding for {0}", nodekey);
                throw e;
            }
        }
        
        /**
         *  The identity MO retrieved from the data store, or null if
         *  the task has not yet executed, there was an error while
         *  executing, or no tasks are assigned to the node.
         * @return the IdentityMO
         */
        public IdentityMO getId() {
            return idmo;
        }
        
        /** 
         * The node for nodeId, or null if the node has failed and been
         * removed from the data store.
         * @return the Node 
         */
        public Node getNode() {
            return node;
        }
    }   
}
