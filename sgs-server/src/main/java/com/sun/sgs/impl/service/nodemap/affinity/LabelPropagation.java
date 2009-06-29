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
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import edu.uci.ics.jung.graph.Graph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  Initial implementation of label propagation algorithm for a single node.
 * <p>
 * An implementation of the algorithm presented in
 * "Near linear time algorithm to detect community structures in large-scale
 * networks" by U.N. Raghavan, R. Albert and S. Kumara 2007
 * <p>
 * Set logging to Level.FINEST for a trace of the algorithm (very verbose
 * and slow).
 * Set logging to Level.FINER to see the final labeled graph.
 * Set logging to Level.FINE and construct with {@code gatherStats} set to
 *  {@code true} to print some high level statistics about each algorithm run.
 */
public class LabelPropagation {
    private static final String PKG_NAME = 
            "com.sun.sgs.impl.service.nodemap.affinity";
    // Our logger
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME));

    // The producer of our graphs.
    private final GraphBuilder builder;

    // A random number generator, to break ties.
    private final Random ran = new Random();

    // Our executor, for running tasks in parallel.
    private final ExecutorService executor;

    // The number of threads this algorithm should use.
    private final int numThreads;

    // If true, gather statistics for each run.
    private final boolean gatherStats;
    // Statistics for the last run, only if gatherStats is true.
    private Collection<AffinityGroup> groups;
    private long time;
    private int iterations;
    private double modularity;

    // The graph in which we're finding communities.  This is a live
    // graph.
    private Graph<LabelVertex, WeightedEdge> graph;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param gatherStats if {@code true}, gather extra statistics for each run.
     *            Useful for testing.
     * @param numThreads number of threads, for TESTING.
     *      If 1, use the sequential asynchronous version.
     *      If >1, use the parallel version, with that number of threads.
     *
     * @throws IllegalArgumentException if {@code numThreads} is
     *       less than {@code 1}
     */
    public LabelPropagation(GraphBuilder builder, boolean gatherStats, 
                            int numThreads)
    {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Num threads must be > 0");
        }
        this.builder = builder;
        this.gatherStats = gatherStats;
        this.numThreads = numThreads;
        if (numThreads > 1) {
            executor = Executors.newFixedThreadPool(numThreads);
        } else {
            executor = null;
        }
    }

    //JANE not sure of how best to return the groups
    /**
     * Find the communities, using a graph obtained from the graph builder
     * provided at construction time.  The communities are found using the
     * label propagation algorithm.
     * <p>
     * This algorithm will not modify the graph by adding or removing vertices
     * or edges, but it will modify the labels in the vertices.
     *
     * @return the affinity groups
     */
    public Collection<AffinityGroup> findCommunities() {
        long startTime = System.currentTimeMillis();

        // Step 1.  Initialize all nodes in the network.
        //          Their labels are their Identities.
        
        // JANE The WeightedGraphBuilder returns a live graphs, the other
        // variations are returning snapshots.  If we got to only the
        // weighted graph builder, can make the graph field final and
        // set it in the constructor.
        graph = createLabelGraph();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                       "Graph creation took {0} milliseconds",
                       (System.currentTimeMillis() - startTime));
        }

        // Step 2.  Set t = 1;
        int t = 1;

        List<LabelVertex> vertices =
                new ArrayList<LabelVertex>(graph.getVertices());
        while (true) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "GRAPH at iteration {0} is {1}",
                                          t, graph);
            }
            // Step 3.  Arrange the nodes in a random order and set it to X.
            // Step 4.  For each vertices in X chosen in that specific order, 
            //          let the label of vertices be the label of the highest
            //          frequency of its neighbors.
            boolean changed = false;

            // Choose a different ordering for each iteration
            if (t > 1) {
                Collections.shuffle(vertices);
            }

            if (numThreads > 1) {
                final AtomicBoolean abool = new AtomicBoolean(false);
                List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
                for (final LabelVertex vertex : vertices) {
                    tasks.add(new Callable<Void>() {
                        public Void call() {
                            boolean res = setMostFrequentLabel(vertex);
                            abool.compareAndSet(false, res);
                            return null;
                        }
                    });
                }

                // Invoke all the tasks, waiting for them to be done.
                // We don't look at the returned futures.
                try {
                    executor.invokeAll(tasks);
                } catch (InterruptedException ie) {
                    changed = true;
                    logger.logThrow(Level.INFO, ie,
                                    " during iteration " + t);
                }
                changed = abool.get();

            } else {
                for (LabelVertex vertex : vertices) {
                    changed = setMostFrequentLabel(vertex) || changed;
                }
            }

            // Step 5. If every node has a label that the maximum number of
            //         their neighbors have, then stop.   Otherwise, set
            //         t++ and loop.
            // Note that Leung's paper suggests we don't need the extra stopping
            // condition if we include each node in the neighbor freq calc.
            if (!changed) {
                break;
            }
            t++;

            if (logger.isLoggable(Level.FINEST)) {
                // Log the affinity groups so far:
                Collection<AffinityGroup> intermediateGroups =
                        gatherGroups(false);
                for (AffinityGroup group : intermediateGroups) {
                    StringBuffer logSB = new StringBuffer();
                    for (Identity id : group.getIdentities()) {
                        logSB.append(id + " ");
                    }
                    logger.log(Level.FINEST,
                               "Intermediate group {0} , members: {1}",
                               group, logSB.toString());
                }

            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "FINAL GRAPH IS {0}", graph);
        }
        groups = gatherGroups(true);

        if (gatherStats) {
            // Record our statistics for this run, used for testing.
            time = System.currentTimeMillis() - startTime;
            iterations = t;
            // Note that the graph might be changing while we ran
            // the algorithm.
            modularity = Graphs.calcModularity(graph, groups);

            if (logger.isLoggable(Level.FINE)) {
                StringBuffer sb = new StringBuffer();
                sb.append(" LPA (" + numThreads + ") took " +
                          time + " milliseconds, " +
                          iterations + " iterations, and found " +
                          groups.size() + " groups ");
                sb.append(" modularity %.4f %n" + modularity);
                for (AffinityGroup group : groups) {
                    sb.append(" id: " + group.getId() + ": members");
                    for (Identity id : group.getIdentities()) {
                        sb.append(id + " ");
                    }
                }
                logger.log(Level.FINE, sb.toString());
            }
        }

        return groups;
    }

    /**
     * Shut down any resources used by this algorithm.
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * Obtains a graph from the graph builder, and converts it into a
     * graph that adds labels to the vertices.  This graph is the first
     * step in our label propagation algorithm (give each graph node a
     * unique label).
     *
     * @return a graph to run the label propagation algorithm over
     */
    private Graph<LabelVertex, WeightedEdge> createLabelGraph() {
        return builder.getAffinityGraph();
    }

    /**
     * Sets the label of {@code node} to the label used most frequently
     * by {@code node}'s neighbors.  Returns {@code true} if {@code node}'s
     * label changed.
     *
     * @param node a vertex in the graph
     * @return {@code true} if {@code node}'s label is changed, {@code false}
     *        if it is not changed
     */
    private boolean setMostFrequentLabel(LabelVertex node) {
        ArrayList<Integer> highestSet = getNeighborCounts(node);

        // If we got back an empty set, no neighbors were found and we're done.
        if (highestSet.isEmpty()) {
            return false;
        }
        
        // If our current label is in the set of highest labels, we're done.
        if (highestSet.contains(node.getLabel())) {
            return false;
        }

        // Otherwise, choose a label at random
        node.setLabel(highestSet.get(ran.nextInt(highestSet.size())));
        logger.log(Level.FINEST, "Returning true: node is now {0}", node);
        return true;
    }

    /**
     * Given a graph, and a node within that graph, find the set of labels
     * with the highest count amongst {@code node}'s neighbors
     *
     * @param node the node whose neighbors labels will be examined
     * @return a list of labels with the higest counts
     */
    private ArrayList<Integer> getNeighborCounts(LabelVertex node) {
        // A map of labels -> count, effectively counting how many
        // of our neighbors use a particular label.
        Map<Integer, Long> labelMap = new HashMap<Integer, Long>();

        // Put our neighbors node into the map.  We allow parallel edges, and
        // use edge weights.
        // NOTE can remove some code if we decide we don't need parallel edges
        StringBuffer logSB = new StringBuffer();
        Collection<LabelVertex> neighbors = graph.getNeighbors(node);
        if (neighbors == null) {
            // No neighbors found: return an empty list.
            return new ArrayList<Integer>();
        }
        for (LabelVertex neighbor : neighbors) {
            if (logger.isLoggable(Level.FINEST)) {
                logSB.append(neighbor + " ");
            }
            Integer label = neighbor.getLabel();
            long value = labelMap.containsKey(label) ?
                         labelMap.get(label) : 0;
            // Use findEdgeSet to allow parallel edges
            Collection<WeightedEdge> edges = graph.findEdgeSet(node, neighbor);
            // edges will be null if node and neighbor are no longer connected;
            // in that case, do nothing
            if (edges != null) {
                for (WeightedEdge edge : edges) {
                    value = value + edge.getWeight();
                }
                labelMap.put(label, value);
            }
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Neighbors of {0} : {1}",
                       node, logSB.toString());
        }

        // Now go through the labelMap, and create a map of
        // values to sets of labels.
        SortedMap<Long, Set<Integer>> countMap =
                new TreeMap<Long, Set<Integer>>();
        for (Map.Entry<Integer, Long> entry : labelMap.entrySet()) {
            long count = entry.getValue();
            Set<Integer> identSet = countMap.get(count);
            if (identSet == null) {
                identSet = new HashSet<Integer>();
            }
            identSet.add(entry.getKey());
            countMap.put(count, identSet);
        }
        // Return the list of labels with the highest count.
        return new ArrayList<Integer>(countMap.get(countMap.lastKey()));
    }

    /**
     * Return the affinity groups found within the graph putting all
     * nodes with the same label in a group.  The affinity group's id
     * will be the label.
     *
     * @param clean if {@code true}, reinitialize the labels
     * @return the affinity groups
     */
    private Collection<AffinityGroup> gatherGroups(boolean clean)
    {
        // All nodes with the same label are in the same community.
        Map<Integer, AffinityGroup> groupMap =
                new HashMap<Integer, AffinityGroup>();
        for (LabelVertex vertex : graph.getVertices()) {
            int label = vertex.getLabel();
            AffinityGroupImpl ag =
                    (AffinityGroupImpl) groupMap.get(label);
            if (ag == null) {
                ag = new AffinityGroupImpl(label);
                groupMap.put(label, ag);
            }
            ag.addIdentity(vertex.getIdentity());
            if (clean) {
                vertex.initializeLabel();
            }
        }
        return groupMap.values();
    }

    /**
     * Returns the time used for the last algorithm run.  This is only
     * valid if we were constructed to gather statistics.
     *
     * @return the time used for the last algorithm run
     */
    public long getTime()         { return time; }

    /**
     * Returns the iterations required for the last algorithm run.  This is only
     * valid if we were constructed to gather statistics.
     *
     * @return the iterations required for the last algorithm run
     */
    public int getIterations()    { return iterations; }

    /**
     * Returns the moduarity of the last algorithm run results. This is only
     * valid if we were constructed to gather statistics.
     *
     * @return the moduarity of the last algorithm run results
     */
    public double getModularity() { return modularity; }
}
