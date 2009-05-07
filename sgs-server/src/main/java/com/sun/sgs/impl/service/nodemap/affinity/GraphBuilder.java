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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The affinity graph builder.  This is used by the GraphListener.  It is 
 * a separate object in case other parts of the system start to feed 
 * information into it (for example, channel information).
 * <p>
 * The data access information naturally forms a bipartite graph, with
 * verticies being either identities or objects, and ownerEdges between them.
 * However, we want a graph with ownerEdges representing object accesses between
 * identities, so we need that graph to be folded.  
 * <p>
 * We build the folded graph on the fly by keeping track of which objects have
 * been used by which identities.  Edges between identies represent an object,
 * and has an associated weight, indicating the number of times both identities
 * have accessed the object.
 * <p>
 * This class is currently assumed to be single threaded.  I'm not sure of JUNGs
 * multithreaded guarantees but we'll have to deal with them with time
 * sensitive graphs.
 * <p>
 * TODO:  drop old data after some time window.  Initial thought:  do this
 *   periodicially, either by number of tasks or time.  Keep track of accesses
 *   in a separate bin, so after the time period is up we can subtract out
 *   accesses in that bin and start keeping track of the new accesses in a new
 *   bin.
 * <p>
 * TODO:  JUNG has graph folding methods, maybe it'd be better to only fold
 *   the graph periodically?  Perhaps maintain bipartite graph, and fold
 *   only when someone asks for the graph?
 */
class GraphBuilder {
    // the base name for properties
    private static final String PROP_BASE = GraphBuilder.class.getName();
    
    // property controlling our time snapshots, in milliseconds
    private static final String PERIOD_PROPERTY = 
            PROP_BASE + ".snapshot.period";
    // default:  5 minutes
    // a longer snapshot gives us more history but also potentially bigger
    // graphs
    private static final long DEFAULT_PERIOD = 1000 * 60 * 5;
    
    // Map for tracking object->identity list
    private final Map<Object, Set<Identity>> objectMap = 
            new HashMap<Object, Set<Identity>>();
    
    // Our graph of object accesses
    private final Graph<Identity, AffinityEdge> affinityGraph = 
            new UndirectedSparseMultigraph<Identity, AffinityEdge>();
    
    // The length of time for our snapshots, in milliseconds
    private final long snapshot;
    
    // number of calls to report, used for printing results every so often
    // this is an aid for early testing
    private long updateCount = 0;
    // part of the statistics for early testing
    private long totalTime = 0;
    
    public GraphBuilder(Properties properties) {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        snapshot = 
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
    }
    
    /**
     * Update the graph based on the objects accessed in a task.
     * 
     * @param owner  the task owner (the object making the accesses)
     * @param detail detailed information about the object accesses, including
     *               a list of the accessed objects
     */
    void buildGraph(Identity owner, AccessedObjectsDetail detail) {
        long startTime = System.currentTimeMillis();
        updateCount++;
        
        affinityGraph.addVertex(owner);

        // For each object accessed in this task...
        for (AccessedObject obj : detail.getAccessedObjects()) {    
            Object objId = obj.getObjectId();

            // find the identities that have already used this object
            Set<Identity> idList = objectMap.get(objId);
            if (idList == null) {
                // first time we've seen this object
                idList = new HashSet<Identity>();
            } else {
                // add or update edges between task owner and identities
                for (Identity ident : idList) {
                    // Our folded graph has no self-loops:  only add an
                    // edge if the identity isn't the owner
                    if (!ident.equals(owner)) {
                        // Check to see if we already have an edge between
                        // owner and ident for this object.  If so, update
                        // the edge weight rather than adding a new edge.
                        boolean addEdge = true;
                        Collection<AffinityEdge> edges = 
                                affinityGraph.findEdgeSet(owner, ident);
                        for (AffinityEdge e : edges) {
                            if (e.getId().equals(objId)) {
                                e.noteObjectAccess(owner);
                                addEdge = false;
                                break;
                            }
                        }
                        
                        if (addEdge) {
                            affinityGraph.addEdge(
                                new AffinityEdge(objId, owner), owner, ident);
                        }
                    }
                    
                }
            }
            idList.add(owner);
            objectMap.put(objId, idList);
        }
        
        totalTime += System.currentTimeMillis() - startTime;
        
        // Print out some data, just for helping look at the algorithms
        // for now
        if (updateCount % 500 == 0) {
            System.out.println("Graph Builder results after " + 
                               updateCount + " updates: ");
            System.out.println("  foldedGraph vertex count: " + 
                               affinityGraph.getVertexCount());
            System.out.println("  foldedGraph edge count: " + 
                               affinityGraph.getEdgeCount());
            System.out.printf("  mean graph processing time: %.2fms %n", 
                              totalTime / (double) updateCount);
        }
    }
    
    /**
     * Returns the current graph, with identities as vertices, and 
     * weighted ownerEdges representing each object accessed by both identity
     * endpoints.
     * 
     * @return the folded graph of accesses
     */
    Graph<Identity, AffinityEdge> getAffinityGraph() {
        return affinityGraph;
    }
}
