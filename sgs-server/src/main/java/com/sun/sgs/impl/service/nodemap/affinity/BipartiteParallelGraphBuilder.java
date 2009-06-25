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
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;

/**
 * A graph builder which builds a bipartite graph of identities and
 * object ids, with edges between them.  Identities never are never
 * liked with other edges, nor are object ids linked to other object ids.
 * <p>
 * This graph builder folds the graph upon request.
 * 
 */
public class BipartiteParallelGraphBuilder extends BipartiteGraphBuilder {
    /**
     * Constructs a new bipartite graph builder.
     * @param props application properties
     */
    public BipartiteParallelGraphBuilder(Properties props) {
        super(props);
    }
    
    /** {@inheritDoc} */
    public Graph<LabelVertex, WeightedEdge> getAffinityGraph() {
        long startTime = System.currentTimeMillis();

        // our folded graph
        Graph<LabelVertex, WeightedEdge> newGraph =
                new UndirectedSparseMultigraph<LabelVertex, WeightedEdge>();

        // vertices in our folded graph
        Collection<Identity> vertices = new HashSet<Identity>();
        
        // Copy our input graph, reducing the amount of time we need to
        // worry about synchronized data
        Graph<Object, WeightedEdge> graphCopy = 
            new CopyableGraph<Object, WeightedEdge>(bipartiteGraph);
        
        System.out.println("Time for graph copy is : " +
                (System.currentTimeMillis() - startTime) + 
                "msec");

        for (Object vert : graphCopy.getVertices()) {
            // This should work, because the types haven't been erased.
            // Testing for String or Long would probably be an issue with
            // the current AccessedObjects, I think -- would need to check
            // again.
            if (vert instanceof Identity) {
                Identity ivert = (Identity) vert;
                vertices.add(ivert);
                newGraph.addVertex(new LabelVertex(ivert));
            }
        }
        
        // would it be better to just use the new graph vertices? 
        // or is this more efficient?
        for (Identity v1 : vertices) {
            LabelVertex labelv1 = new LabelVertex(v1);
            for (Object intermediate : graphCopy.getSuccessors(v1)) {
                for (Object v2 : graphCopy.getSuccessors(intermediate)) {
                    if (v2.equals(v1)) {
                        continue;
                    }
                    LabelVertex labelv2 = new LabelVertex((Identity) v2);
                  
                    boolean addEdge = true;
                    Collection<WeightedEdge> edges =
                            newGraph.findEdgeSet(labelv1, labelv2);

                    for (WeightedEdge e : edges) {
                        if (e instanceof AffinityEdge) {
                            if (((AffinityEdge) e).getId().equals(intermediate))
                            {
                                addEdge = false;
                                break;
                            }
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
                            labelv1, labelv2);
                    }
                }
            } 
        }

       System.out.println(" Folded graph vertex count: " + 
                               newGraph.getVertexCount());
        System.out.println(" Folded graph edge count: " + 
                               newGraph.getEdgeCount());
        System.out.println("Time for graph folding is : " +
                (System.currentTimeMillis() - startTime) + 
                "msec");

        return newGraph;
    }
}

