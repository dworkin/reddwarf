/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import
   com.sun.sgs.impl.service.nodemap.affinity.graph.AbstractAffinityGraphBuilder;
import
    com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilderStats;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.AffinityGraphBuilderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import edu.uci.ics.jung.graph.UndirectedGraph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Graphs;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import javax.management.JMException;

/**
 * A graph builder which builds a bipartite graph of identities and
 * object ids, with edges between them.  Identities are never
 * linked with other edges, nor are object ids linked to other object ids.
 * <p>
 * This graph builder folds the graph upon request.  The folded graph
 * does not contain parallel edges.
 * 
 */
public class BipartiteGraphBuilder extends AbstractAffinityGraphBuilder 
        implements DLPAGraphBuilder
{
    /** The graph of object accesses. */
    private final CopyableGraph<Object, WeightedEdge>
        bipartiteGraph = 
            new CopyableGraph<Object, WeightedEdge>();

    /**
     * A map of identity->graph vertex, allowing fast lookups of particular
     * vertices. This graph is updated when each call to getAffinityGraph
     * is made, so results may be a little stale compared to the
     * bipartite graph.
     */
    private final Map<Identity, LabelVertex> identMap =
            new HashMap<Identity, LabelVertex>();

    /** Our recorded cross-node accesses.  We keep track of this through
     * conflicts detected in data cache kept across nodes;  when a
     * local node is evicted from the cache because of a request from another
     * node for it, we are told of the eviction.
     * Map of object to map of remote nodes it was accessed on, with a weight
     * for each node.
     */
    private final ConcurrentMap<Long, Map<Object, Long>>
        conflictMap = new ConcurrentHashMap<Long, Map<Object, Long>>();

    /** The TimerTask which prunes our data structures over time.  As the data
     * structures above are modified, the pruneTask notes the ways they have
     * changed.  Groups of changes are chunked into periods, each the length
     * of the time snapshot (configured at construction time). We
     * periodically remove the changes made in the earliest snapshot.
     */
    private final PruneTask pruneTask;

    /** Our JMX exposed information. */
    private final AffinityGraphBuilderStats stats;

    // The instantiated algorithm parts.
    /** The core server node portion or null if not valid. */
    private final LabelPropagationServer lpaServer;
    /** The app node portion or null if not valid. */
    private final LabelPropagation lpa;
    /**
     * Constructs a new bipartite graph builder.
     * @param properties the properties for configuring this builder
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @throws Exception if an error occurs
     */
    public BipartiteGraphBuilder(Properties properties,
                                 ComponentRegistry systemRegistry,
                                 TransactionProxy txnProxy)
        throws Exception
    {
        super(properties);
        
        // Create the LPA algorithm pieces
        NodeType type =
            NodeType.valueOf(
                wrappedProps.getProperty(StandardProperties.NODE_TYPE));
        ProfileCollector col =
            systemRegistry.getComponent(ProfileCollector.class);
        WatchdogService wdog = txnProxy.getService(WatchdogService.class);
        if (type == NodeType.coreServerNode) {
            lpaServer = new LabelPropagationServer(col, wdog, properties);
            lpa = null;
            stats = null;
            pruneTask = null;
        } else if (type == NodeType.appNode) {
            lpaServer = null;
            DataService dataService = txnProxy.getService(DataService.class);
            long nodeId = dataService.getLocalNodeId();       
            lpa = new LabelPropagation(this, wdog, nodeId, properties);

            // TODO: Register ourselves with the data servce as a listener
            // for conflict info.

            // Create our JMX MBean
            stats = new AffinityGraphBuilderStats(col,
                        bipartiteGraph, periodCount, snapshot);
            try {
                col.registerMBean(stats,
                                  AffinityGraphBuilderMXBean.MXBEAN_NAME);
            } catch (JMException e) {
                // Continue on if we couldn't register this bean, although
                // it's probably a very bad sign
                logger.logThrow(Level.CONFIG, e, "Could not register MBean");
            }
            pruneTask = new PruneTask(periodCount);
            Timer pruneTimer = new Timer("AffinityGraphPruner", true);
            pruneTimer.schedule(pruneTask, snapshot, snapshot);
        } else {
            throw new IllegalArgumentException(
                    "Cannot use DLPA algorithm on singe node");
        }
    }
    
    /** {@inheritDoc} */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail)
    {
        checkForShutdownState();
        if (state == State.DISABLED) {
            return;
        }
        // We don't ever expect this to be called from the server node!
        assert (stats != null);
        assert (pruneTask != null);

        long startTime = System.currentTimeMillis();
        stats.updateCountInc();
        
        synchronized (bipartiteGraph) {
            bipartiteGraph.addVertex(owner);

            // For each object accessed in this task...
            for (AccessedObject obj : detail.getAccessedObjects()) {    
                Object objId = obj.getObjectId();
                bipartiteGraph.addVertex(objId);
                // We use weighted edges to reduce the total number of edges
                WeightedEdge ae = bipartiteGraph.findEdge(owner, objId);
                if (ae == null) {
                    WeightedEdge newEdge = new WeightedEdge();
                    bipartiteGraph.addEdge(newEdge, owner, objId);
                    // period info
                    pruneTask.incrementEdge(newEdge);
                } else {
                    ae.incrementWeight();
                    // period info
                    pruneTask.incrementEdge(ae);
                }
            }
        }
        stats.processingTimeInc(System.currentTimeMillis() - startTime);
    }

    /** {@inheritDoc} */
    public LabelVertex getVertex(Identity id) {
        return identMap.get(id);
    }

    /** {@inheritDoc} */
    public UndirectedGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        long startTime = System.currentTimeMillis();

        // Copy our input graph
        CopyableGraph<Object, WeightedEdge> graphCopy = 
            new CopyableGraph<Object, WeightedEdge>(bipartiteGraph);
        logger.log(Level.FINE, "Time for graph copy is : {0} msec",
                System.currentTimeMillis() - startTime);

        // Our final, folded graph.  No parallel edges;  they have been
        // collapsed into a single weighted edge.
        UndirectedGraph<LabelVertex, WeightedEdge> foldedGraph =
            new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

        // Clear out our identity->vertex map to prepare for new data.
        identMap.clear();

        // Keep the set of object vertices handy.
        Set<Object> objVerts = new HashSet<Object>();

        // Separate out the vertex set for our new folded graph.
        for (Object vert : graphCopy.getVertices()) {
            if (vert instanceof Identity) {
                Identity ivert = (Identity) vert;
                LabelVertex v = new LabelVertex(ivert);
                foldedGraph.addVertex(v);
                identMap.put(ivert, v);
            } else {
                objVerts.add(vert);
            }
        }

        for (Object objVert : objVerts) {
            // We know objVert is not an Identity because of the loop above.
            List<Object> neighbors =
                    new ArrayList<Object>(graphCopy.getNeighbors(objVert));
            int length = neighbors.size();
            for (int i = 0; i < length - 1; i++) {
                Object neighbor = neighbors.get(i);
                Identity v1 = (Identity) neighbor;

                for (int j = i + 1; j < length; j++) {
                    neighbor = neighbors.get(j);
                    Identity v2 = (Identity) neighbor;

                    // The weight of the edge representing this use
                    // is the min of the counts of each identity's use
                    // of the object
                    long e1Weight = graphCopy.findEdge(v1, objVert).getWeight();
                    long e2Weight = graphCopy.findEdge(v2, objVert).getWeight();
                    long minWeight = Math.min(e1Weight, e2Weight);

                    LabelVertex label1 = getVertex(v1);
                    LabelVertex label2 = getVertex(v2);
                    WeightedEdge edge = foldedGraph.findEdge(label1, label2);
                    if (edge == null) {
                        foldedGraph.addEdge(new WeightedEdge(minWeight),
                                            label1, label2);
                    } else {
                        edge.addWeight(minWeight);
                    }
                }
            }
        }

        // Include the folded time in our total processing time
        stats.processingTimeInc(System.currentTimeMillis() - startTime);
        return Graphs.unmodifiableUndirectedGraph(foldedGraph);
    }

    /** {@inheritDoc} */
    public Map<Long, Map<Object, Long>> getConflictMap() {
        return conflictMap;
    }

    /** {@inheritDoc} */
    public Map<Object, Map<Identity, Long>> getObjectUseMap() {
        Map<Object, Map<Identity, Long>> retMap =
            new HashMap<Object, Map<Identity, Long>>();
        // Copy our input graph
        CopyableGraph<Object, WeightedEdge> graphCopy =
            new CopyableGraph<Object, WeightedEdge>(bipartiteGraph);

        for (Object vert : graphCopy.getVertices()) {
            if (!(vert instanceof Identity)) {
                Map<Identity, Long> idMap = new HashMap<Identity, Long>();
                for (WeightedEdge edge : graphCopy.getIncidentEdges(vert)) {
                    Object v1 = graphCopy.getOpposite(vert, edge);
                    if (v1 instanceof Identity) {
                        idMap.put((Identity) v1, edge.getWeight());
                    } else {
                        // our graph is messed up
                        logger.log(Level.FINE, "unexpected vertex type {0}",
                                                v1);
                    }
                }
                retMap.put(vert, idMap);
            }
        }
        return retMap;
    }

    /** 
     * TBD: This will be the implementation of our conflict detection listener.
     *
     * @param objId the object that was evicted
     * @param nodeId the node that caused the eviction
     * @param forUpdate {@code true} if this eviction was for an update,
     *                  {@code false} if it was for read only access
     */
    public void noteConflictDetected(Object objId, long nodeId,
                                     boolean forUpdate)
    {
        if (objId == null) {
            throw new NullPointerException("objId must not be null");
        }
        Map<Object, Long> objMap = conflictMap.get(nodeId);
        if (objMap == null) {
            Map<Object, Long> newMap = new HashMap<Object, Long>();
            objMap = conflictMap.putIfAbsent(nodeId, newMap);
            if (objMap == null) {
                objMap = newMap;
            }
        }
        synchronized (objMap) {
            Long count = objMap.get(objId);
            long currentVal = (count == null) ? 1 : count + 1;
            objMap.put(objId, currentVal);
        }
        pruneTask.updateConflict(objId, nodeId);
    }

    /** {@inheritDoc} */
    public void removeNode(long nodeId) {
        conflictMap.remove(nodeId);
    }

    /** {@inheritDoc} */
    public void disable() {
        if (setDisabledState()) {
            if (lpaServer != null) {
                lpaServer.disable();
            }
            // nothing special is done for client side
        }
    }
    /** {@inheritDoc} */
    public void enable() {
        if (setEnabledState()) {
            if (lpaServer != null) {
                lpaServer.enable();
            }
            // nothing special is done for client side
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        if (setShutdownState()) {
            if (pruneTask != null) {
                pruneTask.cancel();
            }
            if (lpaServer != null) {
                lpaServer.shutdown();
            }
            if (lpa != null) {
                lpa.shutdown();
            }
        }
    }

    /** {@inheritDoc} */
    public LPAAffinityGroupFinder getAffinityGroupFinder() {
        return lpaServer;
    }

    /**
     * Get the task which prunes the graph.  This is useful for testing.
     *
     * @return the runnable which prunes the graph.
     * @throws UnsupportedOperationException if this builder does not support
     *    graph pruning.
     */
    public Runnable getPruneTask() {
        return pruneTask;
    }

    /**
     * The graph pruner.  It runs periodically, and is the only code
     * that removes edges and vertices from the graph.
     */
    private class PruneTask extends TimerTask {
        // The number of snapshots we retain in our moving window.
        // We fill this window of changes by waiting for count snapshots
        // to occur before we start pruning, ensuring our queues contain
        // count items.  This means we cannot dynamically change the
        // length of the window.
        private final int count;
        // The current snapshot count, used to initially fill up our window.
        private int current = 1;

        // The change information we keep for each snapshot.  A new change info
        // object is allocated for each snapshot, and during a snapshot it
        // notes all changes made to this builder's data structures.
        // Edge -> count of times incremented
        private Map<WeightedEdge, Integer> currentPeriodEdgeIncrements;
        // NodeId -> <ObjId, count times conflicted>
        // Note that the conflict count is not currently used
        private Map<Long, Map<Object, Integer>> currentPeriodConflicts;

        // A lock to guard all uses of the current period information above.
        // Specifically, we want to ensure that updates to these structures
        // aren't ones currently being pruned.
        private final Object currentPeriodLock = new Object();

        // Queues of snapshot information.  As a snapshot time period ends,
        // we add its change info to the back of the appropriate queue.  If
        // we have accumulated enough snapshots in our queues to satisfy our
        // "count" requirement, we also remove the information from the first
        // enqueued info object.
        private final Deque<Map<WeightedEdge, Integer>>
            periodEdgeIncrementsQueue =
                new ArrayDeque<Map<WeightedEdge, Integer>>();
        private final Deque<Map<Long, Map<Object, Integer>>>
            periodConflictQueue =
                new ArrayDeque<Map<Long, Map<Object, Integer>>>();

        /**
         * Creates a PruneTask.
         * @param count the number of full snapshots we wish to
         *              retain as live data
         */
        public PruneTask(int count) {
            this.count = count;
            synchronized (currentPeriodLock) {
                addPeriodStructures();
            }
        }

        /**
         * Performs all processing required when a time period has ended.
         */
        public void run() {
            stats.pruneCountInc();
            // Update the data structures for this snapshot
            synchronized (currentPeriodLock) {
                addPeriodStructures();
                if (current <= count) {
                    // Do nothing, we're still in our inital snapshot window
                    current++;
                    return;
                }
            }

            long startTime = System.currentTimeMillis();

            // take care of everything.
            Map<WeightedEdge, Integer> periodEdgeIncrements =
                    periodEdgeIncrementsQueue.remove();
            Map<Long, Map<Object, Integer>> periodConflicts =
                    periodConflictQueue.remove();

            synchronized (bipartiteGraph) {
                // For each modified edge in the graph, update weights
                for (Map.Entry<WeightedEdge, Integer> entry :
                     periodEdgeIncrements.entrySet())
                {
                    WeightedEdge edge = entry.getKey();
                    int weight = entry.getValue();
                    if (edge.getWeight() == weight) {
                        Pair<Object> endpts = bipartiteGraph.getEndpoints(edge);
                        bipartiteGraph.removeEdge(edge);
                        for (Object end : endpts) {
                            if (bipartiteGraph.degree(end) == 0) {
                                bipartiteGraph.removeVertex(end);
                            }
                        }
                    } else {
                        edge.addWeight(-weight);
                    }
                }
            }

            // For each conflict, update values
            for (Map.Entry<Long, Map<Object, Integer>> entry :
                 periodConflicts.entrySet())
            {
                Long nodeId = entry.getKey();
                Map<Object, Long> objMap = conflictMap.get(nodeId);
                // If the node went down, we might have removed the entry
                if (objMap != null) {
                    synchronized (objMap) {
                        for (Map.Entry<Object, Integer> updateEntry :
                              entry.getValue().entrySet())
                        {
                            Object objId = updateEntry.getKey();
                            Integer periodVal = updateEntry.getValue();
                            Long objMapVal = objMap.get(objId);
                            long newVal =
                                (objMapVal == null) ? 0 : objMapVal - periodVal;
                            if (newVal <= 0) {
                                objMap.remove(objId);
                            } else {
                                objMap.put(objId, newVal);
                            }
                        }
                        if (objMap.isEmpty()) {
                            conflictMap.remove(nodeId);
                        }
                    }
                }
            }
            stats.processingTimeInc(System.currentTimeMillis() - startTime);
        }

        /**
         * Note that an edge's weight has been incremented.
         * Called by a single thread.
         * @param edge the edge
         */
        void incrementEdge(WeightedEdge edge) {
            synchronized (currentPeriodLock) {
                int v = currentPeriodEdgeIncrements.containsKey(edge) ?
                        currentPeriodEdgeIncrements.get(edge) : 0;
                v++;
                currentPeriodEdgeIncrements.put(edge, v);
            }
        }

        /**
         * Note that a data cache conflict has been detected.
         * @param objId the objId of the object causing the conflict
         * @param nodeId the node ID of the node we were in conflict with
         */
        void updateConflict(Object objId, long nodeId) {
            synchronized (currentPeriodLock) {
                Map<Object, Integer> periodObjMap =
                        currentPeriodConflicts.get(nodeId);
                if (periodObjMap == null) {
                    periodObjMap = new HashMap<Object, Integer>();
                    currentPeriodConflicts.put(nodeId, periodObjMap);
                }
                int periodValue = periodObjMap.containsKey(objId) ?
                                  periodObjMap.get(objId) : 0;
                periodValue++;
                periodObjMap.put(objId, periodValue);
            }
        }

        /**
         * Update our queues for this period.
         */
        private void addPeriodStructures() {
            assert Thread.holdsLock(currentPeriodLock);
            currentPeriodEdgeIncrements =
                    new HashMap<WeightedEdge, Integer>();
            periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
            currentPeriodConflicts =
                    new HashMap<Long, Map<Object, Integer>>();
            periodConflictQueue.add(currentPeriodConflicts);
        }
    }

    /**
     * A version of undirected sparse multigraph which has a copy
     * constructor.
     *
     * @param <V>  the vertex type
     * @param <E>  the edge type
     */
    private static class CopyableGraph<V, E> 
            extends UndirectedSparseGraph<V, E>
    {

        /** Serialization version. */
        private static final long serialVersionUID = 1L;

        /**
         * Creates an empty copyable graph.
         */
        public CopyableGraph() {
            super();
        }

        /**
         * Creates a copy of {@code other}.
         * @param other the graph to copy
         */
        public CopyableGraph(CopyableGraph<V, E> other) {
            super();
            synchronized (other) {
                vertices = new HashMap<V, Map<V, E>>(other.vertices);
                edges = new HashMap<E, Pair<V>>(other.edges);
            }
        }
    }
}
