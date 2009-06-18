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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 *  Initial implementation of label propagation algorithm for a single node.
 * <p>
 * An implementation of the algorithm presented in
 * "Near linear time algorithm to detect community structures in large-scale
 * networks" by U.N. Raghavan, R. Albert and S. Kumara 2007
 * 
 */
public class LabelPropagation {
    // The producer of our graphs.
    private final GraphBuilder builder;

    // Algorithm tweaking - is it better to test for convergence, or
    // wait to see if no labels change?
    private final boolean checkConverged;

    // Algorithm tweaking - is it better to include self in list of labels
    // up front?
    private final boolean includeSelf;

    // Statistics for the last successful run, used for testing right now
    private Collection<AffinityGroup> groups;
    private long time;
    private int iterations;
    private double modularity;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param checkConverged {@code true} if we want algorithm to check
     *     for converging, rather than no more labels changing
     */
    public LabelPropagation(GraphBuilder builder, boolean checkConverged, boolean includeSelf) {
        this.builder = builder;
        this.checkConverged = checkConverged;
        this.includeSelf = includeSelf;

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
        long startTime = System.currentTimeMillis();
        // Step 1.  Initialize all nodes in the network.
        //          Their labels are their Identities.
        Graph<LabelNode, WeightedEdge> graph = createLabelGraph();

        System.out.println(" Graph creation took " +
                (System.currentTimeMillis() - startTime) + " milliseconds");
        // Step 2.  Set t = 1;
        int t = 1;

        List<LabelNode> vertices =
                new ArrayList<LabelNode>(graph.getVertices());
        while (true) {
            System.out.println(" iteration " + t);
            System.out.println("GRAPH IS " + graph);
            // Step 3.  Arrange the nodes in a random order and set it to X.
            // Step 4.  For each vertices in X chosen in that specific order, 
            //          let the label of vertices be the label of the highest
            //          frequency of its neighbors.
            boolean changed = false;

            // FOR TESTING:  always randomize the graph, as I'm comparing
            // runs with the same input graph each time
            if (t > 1) {
                // Choose a different ordering
                Collections.shuffle(vertices);
            }
            for (LabelNode vertex : vertices) {
//            for (LabelNode vertex : graph.getVertices()) {
                changed = setMostFrequentLabel(vertex, graph) || changed;
            }

            // Step 5. If every node has a label that the maximum number of
            //         their neighbors have, then stop.   Otherwise, set
            //         t++ and loop.
            // Note that Leung's paper suggests we don't need the extra stopping
            // condition if we include each node in the neighbor freq calc.
            if (checkConverged) {
                if (checkConverged(graph)) {
                    System.out.println("CONVERGED");
                    break;
                }
            } else {
                if (!changed) {
                    break;
                }
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

        // Record our statistics for this run, used for testing.
        time = System.currentTimeMillis() - startTime;
        groups = groupMap.values();
        iterations = t;
        modularity = calcModularity(builder.getAffinityGraph(), groups);

        printStats();

        return groups;
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

        SortedMap<Long, Set<Identity>> countMap =
                getNeighborCounts(node, graph);

        Identity initialLabel = node.label;
        Set<Identity> highestSet = countMap.get(countMap.lastKey());

        // If our current label is in the set of highest labels, we're done.
        Identity mostFrequent;
        if (includeSelf) {
            List<Identity> random = new ArrayList<Identity>(highestSet);
            Collections.shuffle(random);
            mostFrequent = random.get(0);
        } else {
            // Take our node into account here.
            if (highestSet.contains(initialLabel)) {
                mostFrequent = initialLabel;
            } else {
                List<Identity> random = new ArrayList<Identity>(highestSet);
                Collections.shuffle(random);
                mostFrequent = random.get(0);
            }
        }

        System.out.println("Choose label: " + mostFrequent);

        node.label = mostFrequent;
        boolean ret = node.label != initialLabel;
        System.out.println("RETURNING " + ret + " node is now: " + node);
        return (ret);
    }

    private SortedMap<Long, Set<Identity>> getNeighborCounts(LabelNode node,
            Graph<LabelNode, WeightedEdge> graph)
    {
                // A map of labels -> count, effectively counting how many
        // of our neighbors use a particular label.
        Map<Identity, Long> labelMap = new HashMap<Identity, Long>();

        // Put the current node in the map.  This means the current label
        // is added to the neighbor label count.
        if (includeSelf) {
            labelMap.put(node.label, 1L);
        }

        // Put our neighbors node into the map.  We allow parallel edges, and
        // use edge weights.
        // NOTE can remove some code if we decide we don't need parallel edges
        System.out.println("looking for neighbors of " + node);
        for (LabelNode neighbor : graph.getNeighbors(node)) {
            System.out.println("Found neighbor:  " + neighbor);
            Identity label = neighbor.label;
            long value = labelMap.containsKey(label) ?
                         labelMap.get(label) : 0;
            Collection<WeightedEdge> edges = graph.findEdgeSet(node, neighbor);
            for (WeightedEdge edge : edges) {
                value = value + edge.getWeight();
            }
            labelMap.put(label, value);
        }

        // Now go through the labelMap, and create a map of
        // values to sets of labels.
        SortedMap<Long, Set<Identity>> countMap =
                new TreeMap<Long, Set<Identity>>();
        for (Map.Entry<Identity, Long> entry : labelMap.entrySet()) {
            long count = entry.getValue();
            Set<Identity> identSet = countMap.get(count);
            if (identSet == null) {
                identSet = new HashSet<Identity>();
            }
            identSet.add(entry.getKey());
            countMap.put(count, identSet);
        }
        return countMap;
    }

    private boolean checkConverged(Graph<LabelNode, WeightedEdge> graph) {
        for (LabelNode vertex : graph.getVertices()) {
            SortedMap<Long, Set<Identity>> countMap =
                getNeighborCounts(vertex, graph);
            Set<Identity> highestSet = countMap.get(countMap.lastKey());

            if (!highestSet.contains(vertex.label)) {
                return false;
            }
        }
        return true;
    }

    public long getTime()         { return time; }
    public int getIterations()    { return iterations; }
    public double getModularity() { return modularity; }
    
    public void printStats() {
        String con = checkConverged ? "checkConverged" : "noChange";
        String self = includeSelf ? "includeSelf" : "noSelf";
        System.out.print(" LPA (" + con + "," + self + ") took " +
            time + " milliseconds, " + iterations + " iterations, and found " +
            groups.size() + " groups ");
        System.out.printf(" LPA modularity %.4f %n", modularity);
        for (AffinityGroup group : groups) {
            System.out.print(" id: " + group.getId() + ": members: ");
            for (Identity id : group.getIdentities()) {
                System.out.print(id + " ");
            }
            System.out.println(" ");
        }
    }
    
    // This doesn't belong here, it doesn't apply to any particular
    // algorithm for finding affinity groups.
    /**
     * Given a graph and a set of partitions of it, calculate the modularity.
     * Modularity is a quality measure for the goodness of a clustering
     * algorithm, and is, essentially, the number of edges within communities
     * subtracted by the expected number of such edges.
     * <p>
     * The modularity will be a number between 0.0 and 1.0, with a higher
     * number being better.
     * <p>
     * See "Finding community structure in networks using eigenvectors
     * of matrices" 2006 Mark Newman.
     *
     * @param graph the graph which was devided into communities
     * @param groups the communities found in the graph
     * @return
     */
    public static double calcModularity(Graph<Identity, WeightedEdge> graph,
            Collection<AffinityGroup> groups)
    {
        final int m = graph.getEdgeCount();
        final int doublem = 2 * graph.getEdgeCount();

        // For each pair of vertices that are in the same community,
        // compute 1/(2m) * Sum(Aij - Pij), where Pij is kikj/2m.
        // See equation (18) in Newman's paper.
        // Note also that modularity can be expressed as
        // Sum(eii - ai*ai) where eii is the fraction of edges in the group
        // and ai*ai is the expected fraction of edges in the group.
        // See Raghavan paper (JANE also other earlier refs?)
        double q = 0;

        for (AffinityGroup g : groups) {  
            // value is weighted edge count within the community           
            long value = 0;
            // totEdges is the total number of connections for this community
            long totEdges = 0;
            
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(g.getIdentities());
            for (Identity id : groupList) {
                for (WeightedEdge edge : graph.getIncidentEdges(id)) {
                    totEdges = totEdges + edge.getWeight();
                }
            }
            // Look at each of the pairs in the community to find the number
            // of edges within
            while (!groupList.isEmpty()) {
                // Get the first identity
                Identity v1 = groupList.remove(0);
                for (Identity v2 : groupList) {
                    Collection<WeightedEdge> edges = graph.findEdgeSet(v1, v2);
                    // Calculate the adjacency info for v1 and v2
                    // We allow parallel, weighted edges
                    for (WeightedEdge edge : edges) {
                        value = value + (edge.getWeight() * 2);
                    }
                }
            }

            double tmp = (double) totEdges / doublem;
            q = q + (((double) value / doublem) - (tmp * tmp));
        }
        return q;
    }

    /**
     * Calculates Jaccard's index for a pair of affinity groups, which is
     * a measurement of similarity.  The value will be between {@code 0.0}
     * and {@code 1.0}, with higher values indicating stronger similarity
     * between two samples.
     *
     * @param sample1 the first sample
     * @param sample2 the second sample
     * @return the Jaccard index, a value between {@code 0.0} and {@code 1.0},
     *    with higer values indicating more similarity
     */
    public static double calcJaccard(Collection<AffinityGroup> sample1,
                                     Collection<AffinityGroup> sample2)
    {
        // a is number of pairs of identities in same affinity group
        //    in both samples
        // b is number of pairs that are in the same affinity gruop
        //    in the first sample only
        // c is the number of pairs that in the same affinity group
        //    in the second sample only
        long a = 0;
        long b = 0;
        long c = 0;
        for (AffinityGroup group : sample1) {
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(group.getIdentities());
            while (!groupList.isEmpty()) {
                Identity v1 = groupList.remove(0);
                for (Identity v2: groupList) {
                    // v1 and v2 are in the same group in sample1.  Are they
                    // in the same group in sample2?
                    if (inSameGroup(v1, v2, sample2)) {
                        a++;
                    } else {
                        b++;
                    }
                }
            }
        }
        for (AffinityGroup group : sample2) {
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(group.getIdentities());
            while (!groupList.isEmpty()) {
                Identity v1 = groupList.remove(0);
                for (Identity v2: groupList) {
                    // v1 and v2 are in the same group in sample2.  Count those
                    // that are not in the same group in sample1.
                    if (!inSameGroup(v1, v2, sample1)) {
                        c++;
                    }
                }
            }
        }

        // Jaccard's index (or coefficient) is defined as a/(a+b+c).
        return ((double) a / (double) (a + b + c));
    }

    private static boolean inSameGroup(Identity id1, Identity id2,
                                       Collection<AffinityGroup> group)
    {
        for (AffinityGroup g : group) {
            Set<Identity> idents = g.getIdentities();
            if (idents.contains(id1) && idents.contains(id2)) {
                return true;
            }
        }
        return false;
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
