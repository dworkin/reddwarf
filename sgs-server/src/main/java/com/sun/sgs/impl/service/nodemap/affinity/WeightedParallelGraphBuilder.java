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
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A graph builder which builds an affinity graph consisting of identities as
 * vertices and weighted edges for each object used by both identities (a 
 * parallel edge representing each object).
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
 * <p>
 * This class is currently assumed to be single threaded. JUNG provides a
 * way to wrap graphs so they are synchronized;  we might need to use that.
 * 
 */
public class WeightedParallelGraphBuilder implements GraphBuilder {
    // Map for tracking object-> map of identity-> number accesses
    // (thus we keep track of the number of accesses each identity has made
    // for an object, to aid maintaining weighted edges)
    private final Map<Object, Map<Identity, Integer>> objectMap =
            new HashMap<Object, Map<Identity, Integer>>();
    
    // Our graph of object accesses
    private final CopyableGraph<LabelVertex, AffinityEdge> affinityGraph =
            new CopyableGraph<LabelVertex, AffinityEdge>();

    // Our recorded cross-node accesses.  We keep track of this through
    // conflicts detected in data cache kept across nodes;  when a
    // local node is evicted from the cache because of a request from another
    // node for it, we are told of the eviction.
    // Map of object to map of remote nodes it was accessed on, with a weight
    // for each node.
    private final Map<Long, Map<Object, Integer>> conflictMap =
            new ConcurrentHashMap<Long, Map<Object, Integer>>();

    // The length of time for our snapshots, in milliseconds
    private final long snapshot;
    
    // number of calls to report, used for printing results every so often
    // this is an aid for early testing
    private long updateCount = 0;
    // part of the statistics for early testing
    private long totalTime = 0;

    private final PruneTask pruneTask;

    /**
     * Creates a weighted parallel edge graph builder.
     * @param properties  application properties
     */
    public WeightedParallelGraphBuilder(Properties properties) {
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
    public synchronized void updateGraph(Identity owner, 
                                         AccessedObjectsDetail detail)
    {
        long startTime = System.currentTimeMillis();
        updateCount++;

        LabelVertex vowner = new LabelVertex(owner);
        affinityGraph.addVertex(vowner);

        // For each object accessed in this task...
        for (AccessedObject obj : detail.getAccessedObjects()) {    
            Object objId = obj.getObjectId();

            // find the identities that have already used this object
            Map<Identity, Integer> idMap = objectMap.get(objId);
            if (idMap == null) {
                // first time we've seen this object
                idMap = new HashMap<Identity, Integer>();
            }
            
            int value = idMap.containsKey(owner) ? idMap.get(owner) : 0;
            value++;
            
            // add or update edges between task owner and identities
            for (Map.Entry<Identity, Integer> entry : idMap.entrySet()) {
                Identity ident = entry.getKey();

                // Our folded graph has no self-loops:  only add an
                // edge if the identity isn't the owner
                if (!ident.equals(owner)) {
                    long otherValue = entry.getValue();
                    LabelVertex vident = new LabelVertex(ident);

                    // Check to see if we already have an edge between
                    // owner and ident for this object.  If so, update
                    // the edge weight rather than adding a new edge.
                    boolean edgeFound = false;
                    Collection<AffinityEdge> edges = 
                            affinityGraph.findEdgeSet(vowner, vident);
                    for (AffinityEdge e : edges) {
                        if (e.getId().equals(objId)) {
                            // Only update the edge weight if ident has 
                            // accessed the object at least as many times
                            // as owner.
                            // Do we need to worry about values wrapping?
                            // Probably not, if we're removing old data.
                            if (value <= otherValue) {
                                e.incrementWeight();

                                // period info
                                pruneTask.incrementEdge(e);
                            }
                            edgeFound = true;
                            break;
                        }
                    }

                    if (!edgeFound) {
                        AffinityEdge newEdge = new AffinityEdge(objId);
                        affinityGraph.addEdge(newEdge, vowner, vident);
                        // period info
                        pruneTask.incrementEdge(newEdge);
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

    /** {@inheritDoc} */
    public Runnable getPruneTask() {
        return pruneTask;
    }

    /** {@inheritDoc} */
    public Graph<LabelVertex, WeightedEdge> getAffinityGraph() {
        // Copy the graph, to get the edge types correct
        Graph<LabelVertex, WeightedEdge> graphCopy =
            new UndirectedSparseMultigraph<LabelVertex, WeightedEdge>();
        synchronized (affinityGraph) {

            for (LabelVertex id : affinityGraph.getVertices()) {
                graphCopy.addVertex(id);
            }
            for (AffinityEdge e : affinityGraph.getEdges()) {
                Pair<LabelVertex> endpoints = affinityGraph.getEndpoints(e);
                graphCopy.addEdge(new WeightedEdge(e.getWeight()), endpoints);
            }
        }
        return graphCopy;
    }

    /** {@inheritDoc} */
    public Map<Long, Map<Object, Integer>> getConflictMap() {
        return conflictMap;
    }

    /** {@inheritDoc} */
    public Map<Object, Map<Identity, Integer>> getObjectUseMap() {
        return objectMap;
    }


    /** This will be the implementation of our conflict detection listener */
    public void noteConflictDetected(Object objId, long nodeId,
                                     boolean forUpdate)
    {
                Map<Object, Integer> objMap = conflictMap.get(nodeId);
        if (objMap == null) {
            objMap = new ConcurrentHashMap<Object, Integer>();
        }
        int value = objMap.containsKey(objId) ? objMap.get(objId) : 0;
        value++;
        objMap.put(objId, value);
        conflictMap.put(nodeId, objMap);
        pruneTask.updateConflict(objId, nodeId);
    }
    
    private class PruneTask extends TimerTask {
        // JANE what would happen if we made this non-final?
        private final int count;

        private int current = 1;

        private final Queue<Map<Object, Map<Identity, Integer>>>
                                                    periodObjectQueue;
        private final Queue<Map<AffinityEdge, Integer>>
                                                    periodEdgeIncrementsQueue;
        private final Queue<Map<Long, Map<Object, Integer>>>
                                                    periodConflictQueue;
        private Map<Object, Map<Identity, Integer>> currentPeriodObject;
        private Map<AffinityEdge, Integer> currentPeriodEdgeIncrements;
        private Map<Long, Map<Object, Integer>> currentPeriodConflicts;

        public PruneTask(int count) {
            this.count = count;
            periodObjectQueue =
                new LinkedList<Map<Object, Map<Identity, Integer>>>();
            periodEdgeIncrementsQueue =
                new LinkedList<Map<AffinityEdge, Integer>>();
            periodConflictQueue =
                new LinkedList<Map<Long, Map<Object, Integer>>>();
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
            Map<Object, Map<Identity, Integer>> periodObject =
                    periodObjectQueue.remove();
            Map<AffinityEdge, Integer> periodEdgeIncrements =
                    periodEdgeIncrementsQueue.remove();
            Map<Long, Map<Object, Integer>> periodConflicts =
                        periodConflictQueue.remove();

            // For each object, remove the added access counts
            for (Map.Entry<Object, Map<Identity, Integer>> entry :
                 periodObject.entrySet())
            {
                Map<Identity, Integer> idMap = objectMap.get(entry.getKey());
                for (Map.Entry<Identity, Integer> updateEntry :
                     entry.getValue().entrySet())
                {
                    Identity idUpdate = updateEntry.getKey();
                    int newVal = idMap.get(idUpdate) - updateEntry.getValue();
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
            for (Map.Entry<AffinityEdge, Integer> entry :
                 periodEdgeIncrements.entrySet())
            {
                AffinityEdge edge = entry.getKey();
                long weight = entry.getValue();
                if (edge.getWeight() == weight) {
                    Pair<LabelVertex> endpts = affinityGraph.getEndpoints(edge);
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
            // JANE need to lock map?
            for (Map.Entry<Long, Map<Object, Integer>> entry :
                 periodConflicts.entrySet())
            {
                 Long nodeId = entry.getKey();
                 Map<Object, Integer> objMap = conflictMap.get(nodeId);
                 for (Map.Entry<Object, Integer> updateEntry :
                      entry.getValue().entrySet())
                 {
                    Object objId = updateEntry.getKey();
                    int newVal = objMap.get(objId) - updateEntry.getValue();
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

        public synchronized void incrementEdge(AffinityEdge edge) {
            int v = currentPeriodEdgeIncrements.containsKey(edge) ?
                    currentPeriodEdgeIncrements.get(edge) : 0;
            v++;
            currentPeriodEdgeIncrements.put(edge, v);
        }

        public synchronized void updateObjectAccess(Object objId,
                                                    Identity owner)
        {
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
            Map<Object, Integer> periodObjMap =
                    currentPeriodConflicts.get(nodeId);
            if (periodObjMap == null) {
                periodObjMap = new ConcurrentHashMap<Object, Integer>();
            }
            int periodValue = periodObjMap.containsKey(objId) ?
                               periodObjMap.get(objId) : 0;
            periodValue++;
            periodObjMap.put(objId, periodValue);
            currentPeriodConflicts.put(nodeId, periodObjMap);
        }

        private synchronized void addPeriodStructures() {
            currentPeriodObject = new HashMap<Object, Map<Identity, Integer>>();
            periodObjectQueue.add(currentPeriodObject);
            currentPeriodEdgeIncrements = new HashMap<AffinityEdge, Integer>();
            periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
            currentPeriodConflicts =
                    new ConcurrentHashMap<Long, Map<Object, Integer>>();
            periodConflictQueue.add(currentPeriodConflicts);
        }
    }
}
