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

package com.sun.sgs.test.impl.service.task;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.IdentityRelocationListener;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.UnknownNodeException;
import com.sun.sgs.service.WatchdogService;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.HashSet;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.concurrent.atomic.AtomicLong;


/**
 * An in-memory testing implementation of NodeMappingService. Note that
 * this class is not designed to scale, simply to allow some basic tests
 * which require manipulation or observation of the mappings and votes.
 */
public class DummyNodeMappingService implements NodeMappingService,
                                                NodeListener {

    // the map from identities to their current node
    private static ConcurrentHashMap<Identity,Node> nodeMap;
    // the map from identities to their total active votes
    private static ConcurrentHashMap<Identity,AtomicLong> votes;
    // the map from node id to the local service instance
    private static ConcurrentHashMap<Long,DummyNodeMappingService> serviceMap;

    // a node-local map from voting class to the identities it votes active
    private final ConcurrentHashMap<Class<?>,HashSet<Identity>> activeIdentities;
    // a node-local collection of listeners
    private final ConcurrentLinkedQueue<NodeMappingListener> listeners;
    // a node-local collection of known available nodes
    private final ConcurrentLinkedQueue<Node> availableNodes;
    // a node-local reference to the local watchdog service
    private final WatchdogService watchdogService;
    // a node-local copy of the local node's identifier
    private final long localId;

    /** Creates an instance of the service. */
    public DummyNodeMappingService(Properties p, ComponentRegistry cr,
                                   TransactionProxy tp) {
        if (p.getProperty("DummyServer", "false").equals("true")) {
            nodeMap = new ConcurrentHashMap<Identity,Node>();
            votes = new ConcurrentHashMap<Identity,AtomicLong>();
            serviceMap = new ConcurrentHashMap<Long,DummyNodeMappingService>();
        }
        activeIdentities = new ConcurrentHashMap<Class<?>,HashSet<Identity>>();
        listeners = new ConcurrentLinkedQueue<NodeMappingListener>();
        availableNodes = new ConcurrentLinkedQueue<Node>();
        watchdogService = tp.getService(WatchdogService.class);
        watchdogService.addNodeListener(this);
        localId = tp.getService(DataService.class).getLocalNodeId();
        serviceMap.put(localId, this);
    }

    /** {@inheritDoc} */
    public String getName() {
        return getClass().getName();
    }

    /** {@inheritDoc} */
    public void ready() {
        
    }

    /** {@inheritDoc} */
    public void shutdown() {
        
    }

    /** {@inheritDoc} */
    public void assignNode(Class service, Identity identity) {
        assignIdentity(service, identity, chooseNode(identity).getId());
    }

    /** {@inheritDoc} */
    public void setStatus(Class service, Identity identity, 
                          boolean active) throws UnknownIdentityException {
        if (! nodeMap.containsKey(identity))
            throw new UnknownIdentityException("Identity not mapped: " +
                                               identity.getName());

        if (! activeIdentities.containsKey(service))
            activeIdentities.putIfAbsent(service, new HashSet<Identity>());
        HashSet<Identity> set = activeIdentities.get(service);
        boolean update = false;
        synchronized (set) {
            if (set.contains(identity) ^ active) {
                update = true;
                if (active)
                    set.add(identity);
                else
                    set.remove(identity);
            }
        }

        if (update)
            updateVote(identity, active);
    }

    /** {@inheritDoc} */
    public Node getNode(Identity identity) throws UnknownIdentityException {
        Node node = nodeMap.get(identity);
        if (node == null)
            throw new UnknownIdentityException("Identity not mapped: " +
                                               identity.getName());
        return node;
    }

    /** {@inheritDoc} */
    public Iterator<Identity> getIdentities(long nodeId)
        throws UnknownNodeException
    {
        HashSet<Identity> set = new HashSet<Identity>();
        for (Entry<Identity,Node> entry : nodeMap.entrySet()) {
            if (entry.getValue().getId() == nodeId)
                set.add(entry.getKey());
        }
        return set.iterator();
    }

    /** {@inheritDoc} */
    public void addNodeMappingListener(NodeMappingListener listener) {
        listeners.add(listener);
    }

    /** {@inheritDoc} */
    public void nodeStarted(Node node) {
        availableNodes.add(node);
    }

    /** {@inheritDoc} */
    public void nodeFailed(Node node) {
        
    }

    /** {@inheritDoc} */
    public void addIdentityRelocationListener(IdentityRelocationListener listener) { 
        
    }
    
    /** Private helper to choose a mapping node. */
    private Node chooseNode(Identity identity) {
        return availableNodes.peek();
    }

    /** Private helper to update a status vote and react accordingly. */
    private void updateVote(Identity identity, boolean active) {
        long diff = active ? 1L : -1L;
        long result = votes.get(identity).addAndGet(diff);
        if (result == 0) {
            nodeMap.remove(identity);
            votes.remove(identity);
            for (NodeMappingListener listener : listeners)
                listener.mappingRemoved(identity, null);
        }
    }

    /** Start Utility routines here. */

    /** Checks if the given identity is currently mapped. */
    public boolean isMapped(Identity identity) {
        return nodeMap.containsKey(identity);
    }

    /** Returns the current mapping for the given identity, or -1. */
    public long getMapping(Identity identity) {
        Node node = nodeMap.get(identity);
        if (node == null)
            return -1;
        else
            return node.getId();
    }

    /** Returns the total number of active votes for the given identity. */
    public static long getActiveCount(Identity identity) {
        AtomicLong count = votes.get(identity);
        if (count == null)
            return 0;
        return count.get();
    }

    /** Assigns the given identity to the given node. */
    public static void assignIdentity(Class<?> service, Identity identity,
                                      long nodeId) {
        DummyNodeMappingService newService = serviceMap.get(nodeId);
        Node node = newService.watchdogService.getNode(nodeId);
        if (nodeMap.putIfAbsent(identity, node) == null) {
            System.out.println("adding identity: " + identity.getName());
            votes.put(identity, new AtomicLong(0));
            for (NodeMappingListener listener : newService.listeners)
                listener.mappingAdded(identity, null);
        }
        try {
            newService.setStatus(service, identity, true);
        } catch (UnknownIdentityException uie) {}
    }

    /**
     * Tries to move the given identity from the local node to the given
     * node, throwing IllegalArgumentException if the identity is not mapped
     * to the local node or if nodeId is invalid.
     */
    public void moveIdentity(Class<?> service, Identity identity, long nodeId) {
        if (nodeId == localId)
            return;

        Node oldNode = nodeMap.get(identity);
        if (oldNode == null)
            throw new IllegalArgumentException("Unknown identity: " +
                                               identity.getName());
        if (oldNode.getId() != localId)
            throw new IllegalArgumentException("Identity not mapped to this " +
                                               "node: " + identity.getName());

        DummyNodeMappingService newService = serviceMap.get(nodeId);
        Node newNode = watchdogService.getNode(nodeId);
        nodeMap.put(identity, newNode);
        if (service != null) {
            try {
                newService.setStatus(service, identity, true);
            } catch (UnknownIdentityException uie) {}
        }

        for (Entry<Class<?>,HashSet<Identity>> entry :
                 activeIdentities.entrySet()) {
            if (entry.getValue().contains(identity)) {
                try {
                    setStatus(entry.getKey(), identity, false);
                } catch (UnknownIdentityException uie) {}
            }
        }

        for (NodeMappingListener listener : listeners)
            listener.mappingRemoved(identity, newNode);
        for (NodeMappingListener listener : newService.listeners)
            listener.mappingAdded(identity, oldNode);
    }

}
