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
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A graph builder which builds an affinity graph consisting of identities 
 * as vertices and a single weighted edges for each object used by both 
 * identities. 
 * <p>
 * The data access information naturally forms a bipartite graph, with
 * verticies being either identities or objects, and an edge connecting each
 * identity which has accessed an object.
 * However, we want a graph with vertices for identities, and edges 
 * representing object accesses between identities, so we need the bipartite
 * graph to be folded.  
 * <p>
 * We build the folded graph on the fly by keeping track of which objects have
 * been used by which identities.  Edges between identities represent an object,
 * and has an associated weight, indicating the number of times both identities
 * have accessed the object.
 */
public class WeightedGraphBuilder implements GraphBuilder {
    // Map for tracking object-> map of identity-> number accesses
    // (thus we keep track of the number of accesses each identity has made
    // for an object, to aid maintaining weighted edges)
    // Concurrent modifications are protected by locking the affinity graph
    private final Map<Object, Map<Identity, Long>> objectMap =
            new HashMap<Object, Map<Identity, Long>>();
    
    // Our graph of object accesses
    private final UndirectedSparseGraph<LabelVertex, WeightedEdge>
        affinityGraph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

    // Our recorded cross-node accesses.  We keep track of this through
    // conflicts detected in data cache kept across nodes;  when a
    // local node is evicted from the cache because of a request from another
    // node for it, we are told of the eviction.
    // Map of nodes to objects that were evicted to go to that node, with a
    // count.
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
     * Creates a weighted graph builder.
     * @param properties  application properties
     */
    public WeightedGraphBuilder(Properties properties) {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
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
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        long startTime = System.currentTimeMillis();
        updateCount++;

        LabelVertex vowner = new LabelVertex(owner);
        synchronized (affinityGraph) {
            affinityGraph.addVertex(vowner);

            // For each object accessed in this task...
            for (AccessedObject obj : detail.getAccessedObjects()) {
                Object objId = obj.getObjectId();

                // find the identities that have already used this object
                Map<Identity, Long> idMap = objectMap.get(objId);
                if (idMap == null) {
                    // first time we've seen this object
                    idMap = new HashMap<Identity, Long>();
                }

                long value = idMap.containsKey(owner) ? idMap.get(owner) : 0;
                value++;

                // add or update edges between task owner and identities
                for (Map.Entry<Identity, Long> entry : idMap.entrySet()) {
                    Identity ident = entry.getKey();

                    // Our folded graph has no self-loops:  only add an
                    // edge if the identity isn't the owner
                    if (!ident.equals(owner)) {
                        long otherValue = entry.getValue();
                        LabelVertex vident = new LabelVertex(ident);
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
                            if (value <= otherValue) {
                                edge.incrementWeight();
                                // period info
                                pruneTask.incrementEdge(edge);
                            }
                        }
                    }

                }
                idMap.put(owner, value);
                objectMap.put(objId, idMap);

                // period info
                pruneTask.updateObjectAccess(objId, owner);
            }

            totalTime += System.currentTimeMillis() - startTime;

            // Print out some data, just for helping look at the algorithms
            // for now
            if (updateCount % 500 == 0) {
                System.out.println("Weighted Graph Builder results after " +
                                   updateCount + " updates: ");
                System.out.println("  graph vertex count: " +
                                   affinityGraph.getVertexCount());
                System.out.println("  graph edge count: " +
                                   affinityGraph.getEdgeCount());
                System.out.printf("  mean graph processing time: %.2fms %n",
                                  totalTime / (double) updateCount);
            }
        }
    }

    /** {@inheritDoc} */
    public Runnable getPruneTask() {
        return pruneTask;
    }

    /** {@inheritDoc} */
    public UndirectedSparseGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return affinityGraph;
    }

    /** {@inheritDoc} */
    public Map<Object, Map<Identity, Long>> getObjectUseMap() {
        return objectMap;
    }

    /** {@inheritDoc} */
    public ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
            getConflictMap()
    {
        return conflictMap;
    }


    /**
     * This will be the implementation of our conflict detection listener.
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

    /**
     * The graph pruner.  It runs periodically, and is the only code
     * that removes edges and vertices from the graph.
     */
    private class PruneTask extends TimerTask {
        // JANE what would happen if we made count non-final?
        // The number of snapshots we retain in our moving window
        private final int count;
        // The current snapshot count, used to initially fill up our window.
        private int current = 1;

        private final Queue<Map<Object, Map<Identity, Integer>>>
                periodObjectQueue;
        private final Queue<Map<WeightedEdge, Integer>>
                periodEdgeIncrementsQueue;
        private final Queue<ConcurrentMap<Long,
                                          ConcurrentMap<Object, AtomicLong>>>
                periodConflictQueue;

        // ObjId -> <Identity -> count times accessed>
        private Map<Object, Map<Identity, Integer>> currentPeriodObject;
        // Edge -> count of times incremented
        private Map<WeightedEdge, Integer> currentPeriodEdgeIncrements;
        // NodeId -> <ObjId, count times conflicted>
        // Note that the conflict count is not currently used
        private ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
                currentPeriodConflicts;

        public PruneTask(int count) {
            this.count = count;
            periodObjectQueue = 
                new LinkedList<Map<Object, Map<Identity, Integer>>>();
            periodEdgeIncrementsQueue = 
                new LinkedList<Map<WeightedEdge, Integer>>();
            periodConflictQueue =
                new LinkedList<ConcurrentMap<Long,
                                        ConcurrentMap<Object, AtomicLong>>>();
            synchronized (affinityGraph) {
                addPeriodStructures();
            }
        }

        public void run() {
            synchronized (affinityGraph) {
                // Update the data structures for this snapshot
                addPeriodStructures();
                if (current <= count) {
                    // Do nothing, we're still in our inital snapshot window
                    current++;
                    return;
                }

                // take care of everything.
                Map<Object, Map<Identity, Integer>> periodObject =
                        periodObjectQueue.remove();
                Map<WeightedEdge, Integer> periodEdgeIncrements =
                        periodEdgeIncrementsQueue.remove();
                Map<Long, ConcurrentMap<Object, AtomicLong>> periodConflicts =
                        periodConflictQueue.remove();

                // For each object, remove the added access counts
                for (Map.Entry<Object, Map<Identity, Integer>> entry :
                     periodObject.entrySet())
                {
                    Map<Identity, Long> idMap =
                            objectMap.get(entry.getKey());
                    for (Map.Entry<Identity, Integer> updateEntry :
                         entry.getValue().entrySet())
                    {
                        Identity idUpdate = updateEntry.getKey();
                        long newVal =
                            idMap.get(idUpdate) - updateEntry.getValue();
                        if (newVal == 0) {
                            idMap.remove(idUpdate);
                        } else {
                            idMap.put(idUpdate, newVal);
                        }
                    }
                    if (idMap.isEmpty()) {
                        objectMap.remove(entry.getKey());
                    }
                }

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
                            }
                        }
                    } else {
                        edge.addWeight(-weight);
                    }
                }

                // For each conflict, update values
                for (Map.Entry<Long, ConcurrentMap<Object, AtomicLong>> entry :
                     periodConflicts.entrySet())
                {
                    Long nodeId = entry.getKey();
                    ConcurrentMap<Object, AtomicLong> objMap =
                            conflictMap.get(nodeId);
                    // If the node went down, we might have removed the entry
                    if (objMap != null) {
                        for (Map.Entry<Object, AtomicLong> updateEntry :
                              entry.getValue().entrySet())
                        {
                            Object objId = updateEntry.getKey();
                            AtomicLong periodVal = updateEntry.getValue();
                            AtomicLong conflictVal = objMap.get(objId);
                            long oldVal;
                            long newVal;
                            do {
                                oldVal = conflictVal.get();
                                newVal = oldVal - periodVal.get();
                            } while (!conflictVal.compareAndSet(oldVal,
                                                                newVal));

                            if (newVal <= 0) {
                                // This could remove a just incremented value!
                                objMap.remove(objId);
                            }
                        }
                        if (objMap.isEmpty()) {
                            conflictMap.remove(nodeId);
                        }
                    }
                }
            }
        }

        public void incrementEdge(WeightedEdge edge) {
            assert Thread.holdsLock(affinityGraph);
            int v = currentPeriodEdgeIncrements.containsKey(edge) ?
                     currentPeriodEdgeIncrements.get(edge) : 0;
            v++;
            currentPeriodEdgeIncrements.put(edge, v);
        }

        public void updateObjectAccess(Object objId, Identity owner) {
            assert Thread.holdsLock(affinityGraph);
            Map<Identity, Integer> periodIdMap = currentPeriodObject.get(objId);
            if (periodIdMap == null) {
                periodIdMap = new HashMap<Identity, Integer>();
            }
            int periodValue = periodIdMap.containsKey(owner) ?
                              periodIdMap.get(owner) : 0;
            periodValue++;
            periodIdMap.put(owner, periodValue);
            currentPeriodObject.put(objId, periodIdMap);
        }

        public void updateConflict(Object objId, long nodeId) {
            ConcurrentMap<Object, AtomicLong> periodObjMap =
                    currentPeriodConflicts.get(nodeId);
            if (periodObjMap == null) {
                ConcurrentMap<Object, AtomicLong> newMap = 
                        new ConcurrentHashMap<Object, AtomicLong>();
                periodObjMap =
                        currentPeriodConflicts.putIfAbsent(nodeId, newMap);
                if (periodObjMap == null) {
                    periodObjMap = newMap;
                }
            }
            AtomicLong val = periodObjMap.get(objId);
            if (val == null) {
                AtomicLong newVal = new AtomicLong();
                val = periodObjMap.putIfAbsent(objId, newVal);
                if (val == null) {
                    val = newVal;
                }
            }
            val.incrementAndGet();
        }
        
        private void addPeriodStructures() {
            assert Thread.holdsLock(affinityGraph);
            currentPeriodObject = new HashMap<Object, Map<Identity, Integer>>();
            periodObjectQueue.add(currentPeriodObject);
            currentPeriodEdgeIncrements = new HashMap<WeightedEdge, Integer>();
            periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
            currentPeriodConflicts = 
                new ConcurrentHashMap<Long, 
                                        ConcurrentMap<Object, AtomicLong>>();
            periodConflictQueue.add(currentPeriodConflicts);
        }
    }
}
