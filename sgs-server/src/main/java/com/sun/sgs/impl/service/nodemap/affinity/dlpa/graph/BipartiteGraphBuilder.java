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

package com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import
    com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilderStats;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.AffinityGraphBuilderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * A graph builder which builds a bipartite graph of identities and
 * object ids, with edges between them.  Identities never are never
 * liked with other edges, nor are object ids linked to other object ids.
 * <p>
 * This graph builder folds the graph upon request.  The folded graph
 * does not contain parallel edges.
 * 
 */
public class BipartiteGraphBuilder implements DLPAGraphBuilder {
    /** Our property base name. */
    private static final String PROP_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** Our logger. */
    protected static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PROP_NAME));
    
    /** The graph of object accesses. */
    private final CopyableGraph<Object, WeightedEdge>
        bipartiteGraph = 
            new CopyableGraph<Object, WeightedEdge>();

    /** Our recorded cross-node accesses.  We keep track of this through
     * conflicts detected in data cache kept across nodes;  when a
     * local node is evicted from the cache because of a request from another
     * node for it, we are told of the eviction.
     * Map of object to map of remote nodes it was accessed on, with a weight
     * for each node.
     */
    private final ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
        conflictMap =
            new ConcurrentHashMap<Long, ConcurrentMap<Object, AtomicLong>>();

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
     * @param col the profile collector
     * @param props application properties
     * @param nodeId the local node id
     * @throws Exception if an error occurs
     */
    public BipartiteGraphBuilder(ProfileCollector col, Properties props, 
                                 long nodeId)
        throws Exception
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(props);
        long snapshot =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        int periodCount = wrappedProps.getIntProperty(
                PERIOD_COUNT_PROPERTY, DEFAULT_PERIOD_COUNT,
                1, Integer.MAX_VALUE);

        // Create the LPA algorithm pieces
        NodeType type =
            NodeType.valueOf(
                props.getProperty(StandardProperties.NODE_TYPE));
        if (type == NodeType.coreServerNode) {
            lpaServer = new LabelPropagationServer(col, props);
            lpa = null;
        } else if (type == NodeType.appNode) {
            lpaServer = null;
            lpa = new LabelPropagation(this, nodeId, props);
        } else {
            lpaServer = null;
            lpa = null;
        }
        // Create our JMX MBean
        stats = new AffinityGraphBuilderStats(col,
                    bipartiteGraph, periodCount, snapshot);
        try {
            col.registerMBean(stats, AffinityGraphBuilderMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            // Continue on if we couldn't register this bean, although
            // it's probably a very bad sign
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
        pruneTask = new PruneTask(periodCount);
        Timer pruneTimer = new Timer("AffinityGraphPruner", true);
        pruneTimer.schedule(pruneTask, snapshot, snapshot);
    }
    
    /** {@inheritDoc} */
    public synchronized void updateGraph(Identity owner, 
                                         AccessedObjectsDetail detail)
    {
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
    public Runnable getPruneTask() {
        return pruneTask;
    }

    /** {@inheritDoc} */
    public UndirectedSparseGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        long startTime = System.currentTimeMillis();

        // Copy our input graph
        CopyableGraph<Object, WeightedEdge> graphCopy = 
            new CopyableGraph<Object, WeightedEdge>(bipartiteGraph);
        System.out.println("Time for graph copy is : " +
                (System.currentTimeMillis() - startTime) + 
                "msec");
       
        // An intermediate graph, used to track which object paths have
        // been seen.  It contains parallel edges between Identity vertices,
        // with each edge representing a different object.
        Graph<Identity, AffinityEdge> affinityGraph = 
            new UndirectedSparseMultigraph<Identity, AffinityEdge>();

        // vertices in the affinity graph
        Collection<Identity> vertices = new HashSet<Identity>();
        
        // Our final, folded graph.  No parallel edges;  they have been
        // collapsed into a single weighted edge.
        UndirectedSparseGraph<LabelVertex, WeightedEdge> foldedGraph =
            new UndirectedSparseGraph<LabelVertex, WeightedEdge>();
        
        // Separate out the vertex set for our new folded graph.
        for (Object vert : graphCopy.getVertices()) {
            // This should work, because the types haven't been erased.
            // Testing for String or Long would probably be an issue with
            // the current AccessedObjects, I think -- would need to check
            // again.
            if (vert instanceof Identity) {
                Identity ivert = (Identity) vert;
                vertices.add(ivert);
                affinityGraph.addVertex(ivert);
                foldedGraph.addVertex(new LabelVertex(ivert));
            }
        }
        
        for (Identity v1 : vertices) {
            for (Object intermediate : graphCopy.getSuccessors(v1)) {
                for (Object v2 : graphCopy.getSuccessors(intermediate)) {
                    if (v2.equals(v1)) {
                        continue;
                    }
                    Identity v2Ident = (Identity) v2;
                    boolean addEdge = true;
                    for (AffinityEdge e : 
                         affinityGraph.findEdgeSet(v1, v2Ident)) 
                    {
                        if (e.getId().equals(intermediate)) {
                            // ignore; we've already processed this path
                            addEdge = false;
                            break;
                        }
                    }
                    if (addEdge) {                     
                        long e1Weight = 
                            graphCopy.findEdge(v1, intermediate).getWeight();
                        long e2Weight = 
                            graphCopy.findEdge(intermediate, v2).getWeight();
                        long minWeight = Math.min(e1Weight, e2Weight);
                        affinityGraph.addEdge(
                            new AffinityEdge(intermediate, minWeight), 
                            v1, v2Ident);
                    }
                }
            } 
        }

        // Now collapse the parallel edges in the affinity graph
        for (AffinityEdge e : affinityGraph.getEdges()) {
            Pair<Identity> endpoints = affinityGraph.getEndpoints(e);
            LabelVertex v1 = new LabelVertex(endpoints.getFirst());
            LabelVertex v2 = new LabelVertex(endpoints.getSecond());
            WeightedEdge edge = foldedGraph.findEdge(v1, v2);
            if (edge == null) {
                foldedGraph.addEdge(new WeightedEdge(e.getWeight()), v1, v2);
            } else {
                edge.addWeight(e.getWeight());
            }
        }

        // Include the folded time in our total processing time
        stats.processingTimeInc(System.currentTimeMillis() - startTime);
        return foldedGraph;
    }

    /** {@inheritDoc} */
    public ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
            getConflictMap()
    {
        return conflictMap;
    }

    /** {@inheritDoc} */
    public ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>>
            getObjectUseMap()
    {
        ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>> retMap =
            new ConcurrentHashMap<Object,
                                  ConcurrentMap<Identity, AtomicLong>>();
        // Copy our input graph
        CopyableGraph<Object, WeightedEdge> graphCopy =
            new CopyableGraph<Object, WeightedEdge>(bipartiteGraph);

        for (Object vert : graphCopy.getVertices()) {
            if (!(vert instanceof Identity)) {
                ConcurrentMap<Identity, AtomicLong> idMap =
                        new ConcurrentHashMap<Identity, AtomicLong>();
                for (WeightedEdge edge : graphCopy.getIncidentEdges(vert)) {
                    Object v1 = graphCopy.getOpposite(vert, edge);
                    if (v1 instanceof Identity) {
                        idMap.put((Identity) v1, 
                                  new AtomicLong(edge.getWeight()));
                    } else {
                        // our graph is messed up
                        System.out.println("unexpected vertex type " + v1);
                    }
                }
                retMap.put(vert, idMap);
            }
        }
        return retMap;
    }

    /** 
     * This will be the implementation of our conflict detection listener.
     *
     * @param objId the object that was evicted
     * @param nodeId the node that caused the eviction
     * @param forUpdate {@code true} if this eviction was for an update,
     *                  {@code false} if it was for read only access
     */
    public void noteConflictDetected(Object objId, long nodeId,
                                     boolean forUpdate)
    {
        ConcurrentMap<Object, AtomicLong> objMap = conflictMap.get(nodeId);
        if (objMap == null) {
            ConcurrentMap<Object, AtomicLong> newMap =
                    new ConcurrentHashMap<Object, AtomicLong>();
            objMap = conflictMap.putIfAbsent(nodeId, newMap);
            if (objMap == null) {
                objMap = newMap;
            }
        }
        AtomicLong count = objMap.get(objId);
        if (count == null) {
            AtomicLong newCount = new AtomicLong();
            count = objMap.putIfAbsent(objId, newCount);
            if (count == null) {
                count = newCount;
            }
        }
        count.incrementAndGet();
        pruneTask.updateConflict(objId, nodeId);
    }

    /** {@inheritDoc} */
    public void removeNode(long nodeId) {
        conflictMap.remove(nodeId);
    }

    /** {@inheritDoc} */
    public void shutdown() {
        pruneTask.cancel();
        if (lpaServer != null) {
            lpaServer.shutdown();
        }
        if (lpa != null) {
            lpa.shutdown();
        }
    }

    /** {@inheritDoc} */
    public AffinityGroupFinder getAffinityGroupFinder() {
        return lpaServer;
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
                ConcurrentMap<Object, AtomicLong> objMap =
                        conflictMap.get(nodeId);
                // If the node went down, we might have removed the entry
                if (objMap != null) {
                    for (Map.Entry<Object, Integer> updateEntry :
                          entry.getValue().entrySet())
                    {
                        Object objId = updateEntry.getKey();
                        Integer periodVal = updateEntry.getValue();
                        AtomicLong conflictVal = objMap.get(objId);
                        long oldVal;
                        long newVal;
                        do {
                            oldVal = conflictVal.get();
                            newVal = oldVal - periodVal;
                        } while (!conflictVal.compareAndSet(oldVal, newVal));

                        if (newVal <= 0) {
                            // All conflictMap uses are synchrononized so
                            // should be no problem.
                            objMap.remove(objId);
                        }
                    }
                    if (objMap.isEmpty()) {
                        conflictMap.remove(nodeId);
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
            synchronized (currentPeriodLock) {
                currentPeriodEdgeIncrements =
                        new HashMap<WeightedEdge, Integer>();
                periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
                currentPeriodConflicts =
                        new HashMap<Long, Map<Object, Integer>>();
                periodConflictQueue.add(currentPeriodConflicts);
            }
        }
    }

    /**
     * Weighted edges in our affinity graph.  Edges are between two vertices,
     * and contain a weight for the number of times both vertices (identities)
     * have accessed the object this edge represents.
     */
    private static class AffinityEdge extends WeightedEdge {
        // the object this edge represents
        private final Object objId;

        /**
         * Create a new edge with initial weight {@code 1}.
         *
         * @param id  the object id of the object this edge represents
         */
        AffinityEdge(Object id) {
            this(id, 1);
        }

        /**
         * Create a new edge with the given initial weight.
         *
         * @param id  the object id of the object this edge represents
         * @param value the initial weight value
         */
        AffinityEdge(Object id, long value) {
            super(value);
            if (id == null) {
                throw new NullPointerException("id must not be null");
            }
            objId = id;
        }

        /**
         * Returns the object id of the object this edge represents.
         *
         * @return the object id of the object this edge represents
         */
        public Object getId() {
            return objId;
        }

        /** {@inheritDoc} */
        public String toString() {
            return "E:" + objId + ":" + getWeight();
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
