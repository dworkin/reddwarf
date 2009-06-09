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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

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
    public Graph<Identity, WeightedEdge> getAffinityGraph() {
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
        
        // Our final, folded graph.
        Graph<Identity, WeightedEdge> foldedGraph = 
            new UndirectedSparseMultigraph<Identity, WeightedEdge>();
        
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
                foldedGraph.addVertex(ivert);
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
            WeightedEdge edge = foldedGraph.findEdge(endpoints.getFirst(), 
                                                     endpoints.getSecond());
            if (edge == null) {
                foldedGraph.addEdge(new WeightedEdge(e.getWeight()), endpoints);
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

    private class PruneTask extends TimerTask {
        // JANE what would happen if we made this non-final?
        private final int count;

        private int current = 1;

        private final Queue<Map<WeightedEdge, Long>> periodEdgeIncrementsQueue;
        private Map<WeightedEdge, Long> currentPeriodEdgeIncrements;
        public PruneTask(int count) {
            this.count = count;
            periodEdgeIncrementsQueue =
                new LinkedList<Map<WeightedEdge, Long>>();
            addPeriodStructures();
        }
        public synchronized void run() {
            // Update the data structures for this snapshot
            addPeriodStructures();
            if (current <= count) {
                current++;
                return;
            }

            // take care of everything.
            Map<WeightedEdge, Long> periodEdgeIncrements =
                    periodEdgeIncrementsQueue.remove();


            // For each modified edge in the graph, update weights
            for (Map.Entry<WeightedEdge, Long> entry :
                 periodEdgeIncrements.entrySet())
            {
                WeightedEdge edge = entry.getKey();
                long weight = entry.getValue();
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

        public synchronized void incrementEdge(WeightedEdge edge) {
            long v = currentPeriodEdgeIncrements.containsKey(edge) ?
                     currentPeriodEdgeIncrements.get(edge) : 0;
            v++;
            currentPeriodEdgeIncrements.put(edge, v);
        }

        private synchronized void addPeriodStructures() {
            currentPeriodEdgeIncrements = new HashMap<WeightedEdge, Long>();
            periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
        }
    }
}
