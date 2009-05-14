/*
 * Copyright 2009 Sun Microsystems, Inc.
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
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A graph builder which builds a bipartite graph of identities and
 * object ids, with edges between them.  Identities never are never
 * liked with other edges, nor are object ids linked to other object ids.
 * <p>
 * This graph builder folds the graph upon request.
 * 
 */
public class BipartiteGraphBuilder implements GraphBuilder {
    // the base name for properties
    private static final String PROP_BASE = GraphBuilder.class.getName();
    
    // property controlling our time snapshots, in milliseconds
    private static final String PERIOD_PROPERTY = 
        PROP_BASE + ".snapshot.period";
   
    // default:  5 minutes
    // a longer snapshot gives us more history but also potentially bigger
    // graphs
    private static final long DEFAULT_PERIOD = 1000 * 60 * 5;
    
    // Our graph of object accesses
    private final Graph<Object, Long> bipartiteGraph = 
        new UndirectedSparseMultigraph<Object, Long>();
    
    // Edge counter
    private final AtomicLong edgeCount = new AtomicLong();
    
    // The length of time for our snapshots, in milliseconds
    private final long snapshot;
    
    // number of calls to report, used for printing results every so often
    // this is an aid for early testing
    private long updateCount = 0;
    // part of the statistics for early testing
    private long totalTime = 0;
    
    /**
     * Constructs a new bipartite graph builder.
     * @param props application properties
     */
    public BipartiteGraphBuilder(Properties props) {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(props);
        snapshot = 
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
    }
    
    /** {@inheritDoc} */
    public void buildGraph(Identity owner, 
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
                // Would it be easier to use Weighed Edges, rather than
                // parallel ones?
                bipartiteGraph.addEdge(edgeCount.incrementAndGet(), 
                                       owner, objId);
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
        }
    }

    /** {@inheritDoc} */
    public Graph<Identity, AffinityEdge> getAffinityGraph() {
        long startTime = System.currentTimeMillis();

        Graph<Identity, AffinityEdge> newGraph = 
                new UndirectedSparseMultigraph<Identity, AffinityEdge>();

        // Copy our input graph, collapsing parallel edges into weighted
        // edges as we go.
        Graph<Object, WeightedEdge> graphCopy = 
                new UndirectedSparseMultigraph<Object, WeightedEdge>();
        synchronized (bipartiteGraph) {
            for (Object v : bipartiteGraph.getVertices()) {
                graphCopy.addVertex(v);
            }
            for (Long e : bipartiteGraph.getEdges()) {
                Pair<Object> endpoints = bipartiteGraph.getEndpoints(e);
                Object v1 = endpoints.getFirst();
                Object v2 = endpoints.getSecond();
                WeightedEdge ae = graphCopy.findEdge(v1, v2);
                if (ae == null) {
                    graphCopy.addEdge(new WeightedEdge(), v1, v2);
                } else {
                    ae.incrementWeight();
                }
            }
        }
    
        // get vertices for the specified partition, copy into new graph
        Collection<Identity> vertices = new HashSet<Identity>();
        for (Object vert : graphCopy.getVertices()) {
            // This should work, because the types haven't been erased.
            // Testing for String or Long would probably be an issue with
            // the current AccessedObjects, I think -- would need to check
            // again.
            if (vert instanceof Identity) {
                vertices.add((Identity) vert);
                newGraph.addVertex((Identity) vert);
            }
        }
        
        // would it be better to just use the new graph vertices? 
        // or is this more efficient?
        for (Identity v1 : vertices) {
            for (Object intermediate : graphCopy.getSuccessors(v1)) {
                for (Object v2 : graphCopy.getSuccessors(intermediate)) {
                    if (!vertices.contains(v2) || v2.equals(v1)) {
                        continue;
                    }
                    Identity v2Ident = (Identity) v2;

                    
                    boolean addEdge = true;
                    Collection<AffinityEdge> edges = 
                            newGraph.findEdgeSet(v1, v2Ident);

                    for (AffinityEdge e : edges) {
                        if (e.getId().equals(intermediate)) {
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
                        newGraph.addEdge(
                            new AffinityEdge(intermediate, minWeight), 
                            v1, v2Ident);
                    }
                }
            } 
        }
 

        System.out.println("Time for graph folding is : " +
                (System.currentTimeMillis() - startTime) + 
                "msec");

        return newGraph;
    }
}

