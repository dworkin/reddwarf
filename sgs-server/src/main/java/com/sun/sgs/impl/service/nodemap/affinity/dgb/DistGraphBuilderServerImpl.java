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

package com.sun.sgs.impl.service.nodemap.affinity.dgb;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.graph.BasicGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleLabelPropagation;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.UnknownIdentityException;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  The server side of a distributed graph builder for label propagation.
 *  It builds a single graph representing all information for the entire
 *  system, and uses a single node label propagation implementation to
 *  process the graph.
 *  <p>
 * NOTE:  this version does not support graph pruning.
 * 
 */
public class DistGraphBuilderServerImpl 
    implements DistGraphBuilderServer, BasicGraphBuilder, AffinityGroupFinder
{
    // Our package name
    private static final String PKG_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** The property name for the server port. */
    public static final String SERVER_PORT_PROPERTY = PKG_NAME + ".server.port";

    /** The default value of the server port. */
    public static final int DEFAULT_SERVER_PORT = 44537;

    /** The name we export ourselves under. */
    public static final String SERVER_EXPORT_NAME = 
            "DistributedGraphBuilderServer";

    // The exporter for this server
    private final Exporter<DistGraphBuilderServer> exporter;

    // Map for tracking object-> map of identity-> number accesses
    // (thus we keep track of the number of accesses each identity has made
    // for an object, to aid maintaining weighted edges)
    // Concurrent modifications are protected by locking the affinity graph
    private final ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>>
        objectMap =
           new ConcurrentHashMap<Object, ConcurrentMap<Identity, AtomicLong>>();

    // Our graph of object accesses
    private final UndirectedSparseGraph<LabelVertex, WeightedEdge>
        affinityGraph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

    // Our label propagation algorithm
    private final SingleLabelPropagation lpa;

    // The transaction scheduler.
    private final TransactionScheduler transactionScheduler;

    // The task owner.
    private final Identity taskOwner;

    // The supporting node mapping service.
    private final NodeMappingService nms;

    /**
     * Creates a distributed graph builder server.
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @param nms the node mapping service currently being created
     * @param properties  application properties
     * @throws Exception if an error occurs
     */
    DistGraphBuilderServerImpl(ComponentRegistry systemRegistry,
                               TransactionProxy txnProxy,
                               NodeMappingService nms,
                               Properties properties)
            throws Exception
    {
        transactionScheduler =
	    systemRegistry.getComponent(TransactionScheduler.class);
	this.nms = nms;
	taskOwner = txnProxy.getCurrentOwner();
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        int requestedPort = wrappedProps.getIntProperty(
                SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
        // Export ourself.
        exporter =
            new Exporter<DistGraphBuilderServer>(DistGraphBuilderServer.class);
        exporter.export(this, SERVER_EXPORT_NAME, requestedPort);

        ProfileCollector col =
                systemRegistry.getComponent(ProfileCollector.class);
        // Create the LPA algorithm
        lpa = new SingleLabelPropagation(this, col, properties);
    }

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, Object[] objIds) {
        LabelVertex vowner = new LabelVertex(owner);

        // For each object accessed in this task...
        for (Object objId : objIds) {
            // find the identities that have already used this object
            ConcurrentMap<Identity, AtomicLong> idMap = objectMap.get(objId);
            if (idMap == null) {
                // first time we've seen this object
                ConcurrentMap<Identity, AtomicLong> newMap =
                        new ConcurrentHashMap<Identity, AtomicLong>();
                idMap = objectMap.putIfAbsent(objId, newMap);
                if (idMap == null) {
                    idMap = newMap;
                }
            }
            AtomicLong value = idMap.get(owner);
            if (value == null) {
                AtomicLong newVal = new AtomicLong();
                value = idMap.putIfAbsent(owner, newVal);
                if (value == null) {
                    value = newVal;
                }
            }
            long currentVal = value.incrementAndGet();

            synchronized (affinityGraph) {
                affinityGraph.addVertex(vowner);
                // add or update edges between task owner and identities
                for (Map.Entry<Identity, AtomicLong> entry : idMap.entrySet()) {
                    Identity ident = entry.getKey();

                    // Our folded graph has no self-loops:  only add an
                    // edge if the identity isn't the owner
                    if (!ident.equals(owner)) {
                        LabelVertex vident = new LabelVertex(ident);
                        // Check to see if we already have an edge between
                        // the two vertices.  If so, update its weight.
                        WeightedEdge edge =
                                affinityGraph.findEdge(vowner, vident);
                        if (edge == null) {
                            WeightedEdge newEdge = new WeightedEdge();
                            affinityGraph.addEdge(newEdge, vowner, vident);
                        } else {
                            AtomicLong otherValue = entry.getValue();
                            if (currentVal <= otherValue.get()) {
                                edge.incrementWeight();
                            }
                        }
                    }
                }
            }
        }  // objId loop
    }

    // Implement BasicGraphBuilder

    /** {@inheritDoc} */
    public void shutdown() {
        exporter.unexport();
    }

    /** {@inheritDoc} */
    public AffinityGroupFinder getAffinityGroupFinder() {
        return this;
    }

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        throw new UnsupportedOperationException("Unexpected direct update");
    }

    /** {@inheritDoc} */
    public UndirectedSparseGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return affinityGraph;
    }

    /** {@inheritDoc} */
    public Runnable getPruneTask() {
        throw new UnsupportedOperationException("pruning not yet implemented");
    }


    // Implement Affinity Group Finder
    /** {@inheritDoc} */
    public Collection<AffinityGroup> findAffinityGroups() {
        Collection<AffinityGroup> groups = lpa.findAffinityGroups();
        // Need to translate each group into a relocating affinity group
                // Create our final return values
        Collection<AffinityGroup> retVal = new HashSet<AffinityGroup>();
        for (AffinityGroup ag : groups) {
            Map<Identity, Long> idMap = new HashMap<Identity, Long>();
            for (Identity id : ag.getIdentities()) {
                try {
                    GetNodeTask getNodeTask = new GetNodeTask(id);
                    runTransactionally(getNodeTask);
                    idMap.put(id, getNodeTask.getNode().getId());
                } catch (UnknownIdentityException e) {
                    // just leave this one out
                } catch (Exception e) {
                    // ?
                }
            }
            retVal.add(new RelocatingAffinityGroup(ag.getId(), idMap));
        }

        return retVal;
    }

    /** {@inheritDoc} */
    public void removeNode(long nodeId) {
        // do nothing
    }

    /**
     *  Run the given task synchronously, and transactionally, retrying
     *  if the exception is of type <@code ExceptionRetryStatus>.
     * @param task the task
     */
    private void runTransactionally(KernelRunnable task) throws Exception {
        transactionScheduler.runTask(task, taskOwner);
    }

    /**
     * This is a transactional task to obtain the node assignment for
     * a given identity.
     */
    private class GetNodeTask extends AbstractKernelRunnable {

	private final Identity id;
	private volatile Node node = null;

	GetNodeTask(Identity id) {
	    super(null);
	    this.id = id;
	}

	public void run() throws Exception {
	    node = nms.getNode(id);
	}

	Node getNode() {
	    return node;
	}
    }
}
