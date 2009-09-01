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

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.profile.AccessedObjectsDetail;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A graph builder which builds a bipartite graph of identities and
 * object ids, with edges between them.  Identities never are never
 * liked with other edges, nor are object ids linked to other object ids.
 * <p>
 * This graph builder folds the graph upon request.  The folded graph
 * does not contain parallel edges.
 * 
 */
public class BipartiteGraphBuilder implements GraphBuilder {
    /** The graph of object accesses. */
    protected final CopyableGraph<Object, WeightedEdge>
        bipartiteGraph = 
            new CopyableGraph<Object, WeightedEdge>();

    // Our recorded cross-node accesses.  We keep track of this through
    // conflicts detected in data cache kept across nodes;  when a
    // local node is evicted from the cache because of a request from another
    // node for it, we are told of the eviction.
    // Map of object to map of remote nodes it was accessed on, with a weight
    // for each node.
    private final ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
        conflictMap =
            new ConcurrentHashMap<Long, ConcurrentMap<Object, AtomicLong>>();

    // The length of time for our snapshots, in milliseconds
    private final long snapshot;
    
    // number of calls to report, used for printing results every so often
    // this is an aid for early testing
    private long updateCount = 0;
    // part of the statistics for early testing
    private long totalTime = 0;

    private final PruneTask pruneTask;

    /**
     * Constructs a new bipartite graph builder.
     * @param props application properties
     */
    public BipartiteGraphBuilder(Properties props) {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(props);
        snapshot = 
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        int periodCount = wrappedProps.getIntProperty(
                PERIOD_COUNT_PROPERTY, DEFAULT_PERIOD_COUNT,
                0, Integer.MAX_VALUE);

        pruneTask = new PruneTask(periodCount);
        Timer pruneTimer = new Timer("AffinityGraphPruner", true);
        pruneTimer.schedule(pruneTask, snapshot, snapshot);
    }
    
    /** {@inheritDoc} */
    public synchronized void updateGraph(Identity owner, 
                                         AccessedObjectsDetail detail)
    {
        long startTime = System.currentTimeMillis();
        updateCount++;
        
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
        totalTime += System.currentTimeMillis() - startTime;
        
        // Print out some data, just for helping look at the algorithms
        // for now
        if (updateCount % 500 == 0) {
            System.out.println("Bipartite Graph Builder results after " + 
                               updateCount + " updates: ");
            System.out.println("  graph vertex count: " + 
                               bipartiteGraph.getVertexCount());
            System.out.println("  graph edge count: " + 
                               bipartiteGraph.getEdgeCount());
            System.out.printf("  mean graph processing time: %.2fms %n", 
                              totalTime / (double) updateCount);
            getAffinityGraph();
        }
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
        
        System.out.println(" Folded graph vertex count: " + 
                               foldedGraph.getVertexCount());
        System.out.println(" Folded graph edge count: " + 
                               foldedGraph.getEdgeCount());
        System.out.println("Time for graph folding is : " +
                (System.currentTimeMillis() - startTime) + 
                "msec");

        return foldedGraph;
    }

    /** {@inheritDoc} */
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

    private class PruneTask extends TimerTask {
        // The number of snapshots we retain in our moving window.
        // We fill this window of changes by waiting for count snapshots
        // to occur before we start pruning, ensuring our queues contain
        // count items.  This means we cannot dynamically change the
        // length of the window.
        private final int count;

        private int current = 1;

        private final Queue<Map<WeightedEdge, Integer>>
                                                periodEdgeIncrementsQueue;
        private Map<WeightedEdge, Integer> currentPeriodEdgeIncrements;
        private final Queue<ConcurrentMap<Long,
                                          ConcurrentMap<Object, AtomicInteger>>>
                periodConflictQueue;
        private ConcurrentMap<Long, ConcurrentMap<Object, AtomicInteger>>
                currentPeriodConflicts;

        public PruneTask(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("count must not be < 1");
            }
            this.count = count;
            periodEdgeIncrementsQueue =
                new LinkedList<Map<WeightedEdge, Integer>>();
            periodConflictQueue =
                new LinkedList<ConcurrentMap<Long,
                                ConcurrentMap<Object, AtomicInteger>>>();
            addPeriodStructures();
        }

        public synchronized void run() {
            // Update the data structures for this snapshot
            addPeriodStructures();
            if (current <= count) {
                // Do nothing, we're still in our inital snapshot window
                current++;
                return;
            }

            // take care of everything.
            Map<WeightedEdge, Integer> periodEdgeIncrements =
                    periodEdgeIncrementsQueue.remove();
            ConcurrentMap<Long, ConcurrentMap<Object, AtomicInteger>>
                    periodConflicts = periodConflictQueue.remove();


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

            // For each conflict, update values
            for (Map.Entry<Long, ConcurrentMap<Object, AtomicInteger>> entry :
                 periodConflicts.entrySet())
            {
                Long nodeId = entry.getKey();
                ConcurrentMap<Object, AtomicLong> objMap =
                        conflictMap.get(nodeId);
                // If the node went down, we might have removed the entry
                if (objMap != null) {
                    for (Map.Entry<Object, AtomicInteger> updateEntry :
                          entry.getValue().entrySet())
                    {
                        Object objId = updateEntry.getKey();
                        AtomicInteger periodVal = updateEntry.getValue();
                        AtomicLong conflictVal = objMap.get(objId);
                        long oldVal;
                        long newVal;
                        do {
                            oldVal = conflictVal.get();
                            newVal = oldVal - periodVal.get();
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
        }

        synchronized void incrementEdge(WeightedEdge edge) {
            int v = currentPeriodEdgeIncrements.containsKey(edge) ?
                    currentPeriodEdgeIncrements.get(edge) : 0;
            v++;
            currentPeriodEdgeIncrements.put(edge, v);
        }

        synchronized void updateConflict(Object objId, long nodeId) {
            ConcurrentMap<Object, AtomicInteger> periodObjMap =
                    currentPeriodConflicts.get(nodeId);
            if (periodObjMap == null) {
                ConcurrentMap<Object, AtomicInteger> newMap =
                        new ConcurrentHashMap<Object, AtomicInteger>();
                periodObjMap =
                        currentPeriodConflicts.putIfAbsent(nodeId, newMap);
                if (periodObjMap == null) {
                    periodObjMap = newMap;
                }
            }
            AtomicInteger val = periodObjMap.get(objId);
            if (val == null) {
                AtomicInteger newVal = new AtomicInteger();
                val = periodObjMap.putIfAbsent(objId, newVal);
                if (val == null) {
                    val = newVal;
                }
            }
            val.incrementAndGet();
        }

        private synchronized void addPeriodStructures() {
            currentPeriodEdgeIncrements = new HashMap<WeightedEdge, Integer>();
            periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
            currentPeriodConflicts =
                new ConcurrentHashMap<Long, 
                                ConcurrentMap<Object, AtomicInteger>>();
            periodConflictQueue.add(currentPeriodConflicts);
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
