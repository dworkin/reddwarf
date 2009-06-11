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
//import com.sun.sgs.impl.service.nodemap.affinity.LabelPropagation.LabelNode;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Initial implementation of label propagation algorithm for a single node.
 * 
 */
public class LabelPropagation {
    // The producer of our graphs.
    private final GraphBuilder builder;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     */
    public LabelPropagation(GraphBuilder builder) {
        this.builder = builder;

    }

    //JANE not sure of how best to return the groups
    /**
     * Find the communities, using a graph obtained from the graph builder
     * provided at construction time.  The communities are found using the
     * label propagation algorithm.
     *
     * @return the affinity groups
     */
    public Collection<AffinityGroup> findCommunities() {
        // Step 1.  Initialize all nodes in the network.
        //          Their labels are their Identities.
        Graph<LabelNode, WeightedEdge> graph = createLabelGraph();

        // Step 2.  Set t = 1;
        int t = 1;
        
        // This code is synchronous!
        while (true) {
            System.out.println(" iteration " + t);
            System.out.println("GRAPH IS " + graph);
            // Step 3.  Arrange the nodes in a random order and set it to X.
            // Step 4.  For each x in X chosen in that specific order, let
            //          the label of x be the label of the highest frequency of
            //          its neighbors.
            boolean changed = false;
            for (LabelNode vertex : graph.getVertices()) {
                changed = setMostFrequentLabel(vertex, graph) || changed;
            }

            // Step 5. If every node has a label that the maximum number of
            //         their neighbors have, then stop.   Otherwise, set
            //         t++ and loop.
            if (!changed) {
                break;
            }
            t++;

            // For TESTING:
            // Compute the affinity groups so far:
            Map<Identity, AffinityGroup> groupMap =
                new HashMap<Identity, AffinityGroup>();
            for (LabelNode vertex : graph.getVertices()) {
                AffinityGroupImpl ag =
                        (AffinityGroupImpl) groupMap.get(vertex.label);
                if (ag == null) {
                    ag = new AffinityGroupImpl();
                    groupMap.put(vertex.label, ag);
                }
                ag.addIdentity(vertex.id);
            }
            for (AffinityGroup group : groupMap.values()) {
                System.out.println("ZZZ " + group + " , members:");
                for (Identity id : group.getIdentities()) {
                    System.out.println(id);
                }
            }
        }

        System.out.println("FINAL GRAPH IS " + graph);
        // All nodes with the same label are in the same community.
        Map<Identity, AffinityGroup> groupMap =
                new HashMap<Identity, AffinityGroup>();
        for (LabelNode vertex : graph.getVertices()) {
            AffinityGroupImpl ag = 
                    (AffinityGroupImpl) groupMap.get(vertex.label);
            if (ag == null) {
                ag = new AffinityGroupImpl();
                groupMap.put(vertex.label, ag);
            }
            ag.addIdentity(vertex.id);
        }
        return groupMap.values();
    }

    /**
     * Obtains a graph from the graph builder, and converts it into a
     * graph that adds labels to the vertices.  This graph is the first
     * step in our label propagation algorithm (give each graph node a
     * unique label).
     *
     * @return a graph to run the label propagation algorithm over
     */
    private Graph<LabelNode, WeightedEdge> createLabelGraph() {
        Graph<LabelNode, WeightedEdge> graph =
                new UndirectedSparseMultigraph<LabelNode, WeightedEdge>();
        Graph<Identity, WeightedEdge> affinityGraph =
                builder.getAffinityGraph();

        Map<Identity, LabelNode> nodeMap = new HashMap<Identity, LabelNode>();
        for (Identity id : affinityGraph.getVertices()) {
            LabelNode newNode = new LabelNode(id);
            graph.addVertex(newNode);
            nodeMap.put(id, newNode);
        }
        for (WeightedEdge e : affinityGraph.getEdges()) {
            Pair<Identity> endpoints = affinityGraph.getEndpoints(e);
            graph.addEdge(new WeightedEdge(e.getWeight()),
                                          nodeMap.get(endpoints.getFirst()),
                                          nodeMap.get(endpoints.getSecond()));
        }

        return graph;

    }

    /**
     * Sets the label of {@code node} to the label used most frequently
     * by {@code node}'s neighbors.  Returns false if {@code node}'s label
     * was unchanged.
     *
     * @param node a vertex
     * @param graph the full graph
     * @return {@code false} if {@code node}'s label is unchanged
     */
    private boolean setMostFrequentLabel(LabelNode node,
            Graph<LabelNode, WeightedEdge> graph)
    {
        Map<Identity, Long> labelMap = new HashMap<Identity, Long>();
        // Find the neighbors
        System.out.println("looking for neighbors of " + node);

        // Put this node into the map
        labelMap.put(node.label, 1L);
        for (LabelNode neighbor : graph.getNeighbors(node)) {
            System.out.println("Found neighbor:  " + neighbor);
            Identity label = neighbor.label;
            long value = labelMap.containsKey(label) ?
                         labelMap.get(label) : 0;
            value++;
            labelMap.put(label, value);
        }


        Identity initialLabel = node.label;
        List<Identity> random = new ArrayList<Identity>(labelMap.keySet());
        Collections.shuffle(random);
        Identity mostFrequent = random.get(0);
        System.out.println("Choose random label: " + mostFrequent);
        
        long count = 1;
        for (Map.Entry<Identity, Long> entry : labelMap.entrySet()) {
            if (entry.getValue() > count) {
                count = entry.getValue();
                System.out.println("setting mostFrequent to " + entry.getKey());
                mostFrequent = entry.getKey();
            }
        }
        node.label = mostFrequent;
        boolean ret = node.label != initialLabel;
        System.out.println("RETURNING " + ret + " node is now: " + node);
        return (ret);
    }

    private static class LabelNode {
        Identity id;
        Identity label;

        public LabelNode(Identity id) {
            this.id = id;
            this.label = id;
        }

        public String toString() {
            return "[" + id.toString() + ":" + label.toString() + "]";
        }
    }
}
