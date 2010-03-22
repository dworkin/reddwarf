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
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import javax.management.JMException;

/**
 * A graph builder which builds an affinity graph consisting of identities 
 * as vertices and a single weighted edges representing objects used by both
 * identities. 
 * <p>
 * The data access information naturally forms a bipartite graph, with
 * vertices being either identities or objects, and an edge connecting each
 * identity which has accessed an object.
 * However, we want a graph with vertices for identities, and edges 
 * representing object accesses between identities, so we need the bipartite
 * graph to be folded.  
 * <p>
 * We build the folded graph on the fly by keeping track of which objects have
 * been used by which identities.  Edges between identities are weighted, and
 * represent the number of object accesses the two identities have in common.
 */
public class WeightedGraphBuilder extends AbstractAffinityGraphBuilder 
        implements DLPAGraphBuilder
{
    /** Map for tracking object-> map of identity-> number accesses
     * (thus we keep track of the number of accesses each identity has made
     * for an object, to aid maintaining weighted edges)
     */
    private final ConcurrentMap<Object, Map<Identity, Long>>
        objectMap = new ConcurrentHashMap<Object, Map<Identity, Long>>();
    
    /** Our graph of object accesses.  Changes to the graph must be made
     * with the graph locked.
     */
    private final UndirectedGraph<LabelVertex, WeightedEdge>
        affinityGraph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

    /**
     * A map of identity->graph vertex, allowing fast lookups of particular
     * vertices. Changes to this map should occur atomically with vertex
     * changes to the affinity graph, with a lock held on the
     * {@code affinityGraph}.
     */
    private final Map<Identity, LabelVertex> identMap =
            new HashMap<Identity, LabelVertex>();

    /** Our recorded cross-node accesses.  We keep track of this through
     * conflicts detected in data cache kept across nodes;  when a
     * local node is evicted from the cache because of a request from another
     * node for it, we are told of the eviction.
     * Map of nodes to objects that were evicted to go to that node, with a
     * count.
     * TBD: consider changing this to another data structure not using
     * inner maps.
     */
    private final ConcurrentMap<Long, Map<Object, Long>> conflictMap =
        new ConcurrentHashMap<Long, Map<Object, Long>>();

    /** The TimerTask which prunes our data structures over time.  As the data
     * structures above are modified, the pruneTask notes the ways they have
     * changed.  Groups of changes are chunked into periods, each the length
     * of the time snapshot (configured at construction time). We
     * periodically remove the changes made in the earliest snapshot.
     * The pruneTask is {@code null} on the core server node because no
     * graph information is accumulated on that node.
     */
    private final PruneTask pruneTask;

    /** Our JMX exposed information, or {@code null} if we are on the 
     * core server node.  No graph actions occur on the core server node.
     */
    private final AffinityGraphBuilderStats stats;

    // Our label propagation algorithm parts
    /** The core server node portion or null if this is an app node. */
    private final LabelPropagationServer lpaServer;
    /** The app node portion or null if this is an app node. */
    private final LabelPropagation lpa;

    /**
     * Creates a weighted graph builder.
     * @param properties the properties for configuring this builder
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @throws Exception if an error occurs
     */
    public WeightedGraphBuilder(Properties properties,
                                ComponentRegistry systemRegistry,
                                TransactionProxy txnProxy)
        throws Exception
    {
        super(properties);

        WatchdogService wdog = txnProxy.getService(WatchdogService.class);
        // Create the LPA algorithm pieces
        NodeType type =
            NodeType.valueOf(
                properties.getProperty(StandardProperties.NODE_TYPE));
        ProfileCollector col =
            systemRegistry.getComponent(ProfileCollector.class);
        if (type == NodeType.coreServerNode) {
            lpaServer = new LabelPropagationServer(col, wdog, properties);
            lpa = null;
            pruneTask = null;
            stats = null;
        } else if (type == NodeType.appNode) {
            lpaServer = null;
            DataService dataService = txnProxy.getService(DataService.class);
            long nodeId = dataService.getLocalNodeId();
            lpa = new LabelPropagation(this, wdog, nodeId, properties);

            // TODO: Register ourselves with the data servce as a listener
            // for conflict info.

            // Create our JMX MBean
            stats = new AffinityGraphBuilderStats(col,
                        affinityGraph, periodCount, snapshot);
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
                    "Cannot use DLPA algorithm on single node");
        }
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * This method is called by a single thread but must protect itself
     * from changes to data structures made by the pruner.
     */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        checkForShutdownState();
        if (state == State.DISABLED) {
            return;
        }

        // We don't ever expect this to be called from the server node!
        assert (stats != null);
        assert (pruneTask != null);
        long startTime = System.currentTimeMillis();
        stats.updateCountInc();

        // For each object accessed in this task...
        for (AccessedObject obj : detail.getAccessedObjects()) {
            Object objId = obj.getObjectId();

            // find the identities that have already used this object
            Map<Identity, Long> idMap = objectMap.get(objId);
            if (idMap == null) {
                // first time we've seen this object
                Map<Identity, Long> newMap = new HashMap<Identity, Long>();
                idMap = objectMap.putIfAbsent(objId, newMap);
                if (idMap == null) {
                    idMap = newMap;
                }
            }
            long currentVal;
            synchronized (idMap) {
                Long val = idMap.get(owner);
                currentVal = (val == null) ? 1 : val + 1;
                idMap.put(owner, currentVal);
            }

            synchronized (affinityGraph) {
                // Add the vertex while synchronized to ensure no interference
                // from the graph pruner.
                LabelVertex vowner = addOrGetVertex(owner);
                // add or update edges between task owner and identities
                for (Map.Entry<Identity, Long> entry : idMap.entrySet()) {
                    Identity ident = entry.getKey();

                    // Our folded graph has no self-loops:  only add an
                    // edge if the identity isn't the owner
                    if (!ident.equals(owner)) {
                        LabelVertex vident = addOrGetVertex(ident);
                        // Check to see if we already have an edge between
                        // the two vertices.  If so, update its weight.
                        WeightedEdge edge =
                                affinityGraph.findEdge(vowner, vident);
                        if (edge == null) {
                            WeightedEdge newEdge = new WeightedEdge();
                            affinityGraph.addEdge(newEdge, vowner, vident);
                            // period info
                            pruneTask.incrementEdge(newEdge);
                        } else {
                            if (currentVal <= entry.getValue()) {
                                edge.incrementWeight();
                                // period info
                                pruneTask.incrementEdge(edge);
                            }
                        }

                    }
                }
            }

            // period info
            pruneTask.updateObjectAccess(objId, owner);
        }

        stats.processingTimeInc(System.currentTimeMillis() - startTime);
    }

    /** {@inheritDoc} */
    public UndirectedGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return Graphs.unmodifiableUndirectedGraph(
                Graphs.synchronizedUndirectedGraph(affinityGraph));
    }

    /** {@inheritDoc} */
    public Map<Object, Map<Identity, Long>> getObjectUseMap() {
        return objectMap;
    }

    /** {@inheritDoc} */
    public Map<Long, Map<Object, Long>> getConflictMap() {
        return conflictMap;
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
    public LabelVertex getVertex(Identity id) {
        synchronized (affinityGraph) {
            return identMap.get(id);
        }
    }
    
    /** {@inheritDoc} */
    public LPAAffinityGroupFinder getAffinityGroupFinder() {
        return lpaServer;
    }
    
    /**
     * TBD: This will be the implementation of our conflict detection listener.
     * <p>
     * Note that forUpdate is currently not used.
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

    /**
     * Adds a vertex for the given identity to the graph, or retrieve the
     * existing one.
     * @param id the identity
     * @return the graph vertex representing the identity
     */
    private LabelVertex addOrGetVertex(Identity id) {
        assert Thread.holdsLock(affinityGraph);
        LabelVertex v = getVertex(id);
        if (v == null) {
            v = new LabelVertex(id);
            affinityGraph.addVertex(v);
            identMap.put(id, v);
        }
        return v;
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
        // We run long enough to fill at least one window before the pruner
        // can begin.
        private int current = 1;

        // The change information we keep for each snapshot.  A new change info
        // object is allocated for each snapshot, and during a snapshot it
        // notes all changes made to this builder's data structures.
        // ObjId -> <Identity -> count times accessed>
        private Map<Object, Map<Identity, Integer>> currentPeriodObject;
        // Edge -> count of times incremented
        private Map<WeightedEdge, Integer> currentPeriodEdgeIncrements;
        // NodeId -> <ObjId, count times conflicted>
        // Note that the conflict count is not currently used
        private Map<Long, Map<Object, Integer>> currentPeriodConflicts;

        // Queues of snapshot information.  As a snapshot time period ends,
        // we add its change info to the back of the appropriate queue.  If
        // we have accumulated enough snapshots in our queues to satisfy our
        // "count" requirement, we also remove the information from the first
        // enqueued info object.
        private final Deque<Map<Object, Map<Identity, Integer>>>
            periodObjectQueue =
                new ArrayDeque<Map<Object, Map<Identity, Integer>>>();
        private final Deque<Map<WeightedEdge, Integer>>
            periodEdgeIncrementsQueue =
                new ArrayDeque<Map<WeightedEdge, Integer>>();
        private final Deque<Map<Long, Map<Object, Integer>>>
            periodConflictQueue =
                new ArrayDeque<Map<Long, Map<Object, Integer>>>();

        // A lock to guard all uses of the current period information above
        // and the queues.
        // Specifically, we want to ensure that updates to these structures
        // aren't ones currently being pruned.
        private final Object currentPeriodLock = new Object();

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
            Map<Object, Map<Identity, Integer>> periodObject;
            Map<WeightedEdge, Integer> periodEdgeIncrements;
            Map<Long, Map<Object, Integer>> periodConflicts;

            // Note: We want to make sure we don't have snapshots that are so
            // short that we cannot do all our pruning within one.
            synchronized (currentPeriodLock) {
                // Add the data structures for this new period that is just
                // starting.
                addPeriodStructures();
                if (current <= count) {
                    // We're still in our inital time window, and haven't
                    // gathered enough periods yet.
                    current++;
                    return;
                }
                // Remove the earliest snasphot.
                periodObject = periodObjectQueue.removeFirst();
                periodEdgeIncrements = periodEdgeIncrementsQueue.removeFirst();
                periodConflicts = periodConflictQueue.removeFirst();
            }

            long startTime = System.currentTimeMillis();

            // For each object, remove the added access counts
            for (Map.Entry<Object, Map<Identity, Integer>> entry :
                periodObject.entrySet())
            {
                Map<Identity, Long> idMap = objectMap.get(entry.getKey());
                synchronized (idMap) {
                    for (Map.Entry<Identity, Integer> updateEntry :
                         entry.getValue().entrySet())
                    {
                        Identity updateId = updateEntry.getKey();
                        long updateValue = updateEntry.getValue();
                        Long idMapVal = idMap.get(updateId);
                        long newVal =
                            (idMapVal == null) ? 0 : idMapVal - updateValue;
                        if (newVal <= 0) {
                            idMap.remove(updateId);
                        } else {
                            idMap.put(updateId, newVal);
                        }
                    }
                    if (idMap.isEmpty()) {
                        objectMap.remove(entry.getKey(), idMap);
                    }
                }
            }

            synchronized (affinityGraph) {
                // For each modified edge in the graph, update weights
                for (Map.Entry<WeightedEdge, Integer> entry :
                     periodEdgeIncrements.entrySet())
                {
                    WeightedEdge edge = entry.getKey();
                    int weight = entry.getValue();
                    if (edge.getWeight() == weight) {
                        Pair<LabelVertex> endpts =
                                affinityGraph.getEndpoints(edge);
                        affinityGraph.removeEdge(edge);
                        for (LabelVertex end : endpts) {
                            if (affinityGraph.degree(end) == 0) {
                                affinityGraph.removeVertex(end);
                                identMap.remove(end.getIdentity());
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
         * Note that an object has been accessed.
         * Called by a single thread.
         * @param objId the object
         * @param owner the accessor
         */
        void updateObjectAccess(Object objId, Identity owner) {
            synchronized (currentPeriodLock) {
                Map<Identity, Integer> periodIdMap =
                        currentPeriodObject.get(objId);
                if (periodIdMap == null) {
                    periodIdMap = new HashMap<Identity, Integer>();
                    currentPeriodObject.put(objId, periodIdMap);
                }
                int periodValue = periodIdMap.containsKey(owner) ?
                                  periodIdMap.get(owner) : 0;
                periodValue++;
                periodIdMap.put(owner, periodValue);
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
            currentPeriodObject =
                    new HashMap<Object, Map<Identity, Integer>>();
            periodObjectQueue.addLast(currentPeriodObject);
            currentPeriodEdgeIncrements =
                    new HashMap<WeightedEdge, Integer>();
            periodEdgeIncrementsQueue.addLast(currentPeriodEdgeIncrements);
            currentPeriodConflicts =
                    new HashMap<Long, Map<Object, Integer>>();
            periodConflictQueue.addLast(currentPeriodConflicts);
        }
    }
}
