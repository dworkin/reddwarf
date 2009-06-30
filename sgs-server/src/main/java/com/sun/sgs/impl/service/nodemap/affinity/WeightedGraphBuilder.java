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
import edu.uci.ics.jung.graph.util.Pair;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

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
    private final Map<Object, Map<Identity, Long>> objectMap = 
            new HashMap<Object, Map<Identity, Long>>();
    
    // Our graph of object accesses
    private final UndirectedSparseGraph<LabelVertex, WeightedEdge>
        affinityGraph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();
    
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
        synchronized(affinityGraph) {
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
    public Graph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return affinityGraph;
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

        private final Queue<Map<Object, Map<Identity, Long>>> periodObjectQueue;
        private final Queue<Map<WeightedEdge, Long>> periodEdgeIncrementsQueue;
        private Map<Object, Map<Identity, Long>> currentPeriodObject;
        private Map<WeightedEdge, Long> currentPeriodEdgeIncrements;
        public PruneTask(int count) {
            this.count = count;
            periodObjectQueue = 
                new LinkedList<Map<Object, Map<Identity, Long>>>();
            periodEdgeIncrementsQueue = 
                new LinkedList<Map<WeightedEdge, Long>>();
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
                Map<Object, Map<Identity, Long>> periodObject =
                        periodObjectQueue.remove();
                Map<WeightedEdge, Long> periodEdgeIncrements =
                        periodEdgeIncrementsQueue.remove();

                // For each object, remove the added access counts
                for (Map.Entry<Object, Map<Identity, Long>> entry :
                     periodObject.entrySet())
                {
                    Map<Identity, Long> idMap = objectMap.get(entry.getKey());
                    for (Map.Entry<Identity, Long> updateEntry :
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
                for (Map.Entry<WeightedEdge, Long> entry :
                     periodEdgeIncrements.entrySet())
                {
                    WeightedEdge edge = entry.getKey();
                    long weight = entry.getValue();
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
            }
        }

        public void incrementEdge(WeightedEdge edge) {
            assert Thread.holdsLock(affinityGraph);
            long v = currentPeriodEdgeIncrements.containsKey(edge) ?
                     currentPeriodEdgeIncrements.get(edge) : 0;
            v++;
            currentPeriodEdgeIncrements.put(edge, v);
        }

        public void updateObjectAccess(Object objId,
                                                    Identity owner)
        {
            assert Thread.holdsLock(affinityGraph);
            Map<Identity, Long> periodIdMap = currentPeriodObject.get(objId);
            if (periodIdMap == null) {
                periodIdMap = new HashMap<Identity, Long>();
            }
            long periodValue = periodIdMap.containsKey(owner) ?
                               periodIdMap.get(owner) : 0;
            periodValue++;
            periodIdMap.put(owner, periodValue);
            currentPeriodObject.put(objId, periodIdMap);
        }

        private void addPeriodStructures() {
            assert Thread.holdsLock(affinityGraph);
            currentPeriodObject = new HashMap<Object, Map<Identity, Long>>();
            periodObjectQueue.add(currentPeriodObject);
            currentPeriodEdgeIncrements = new HashMap<WeightedEdge, Long>();
            periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
        }
    }
}
