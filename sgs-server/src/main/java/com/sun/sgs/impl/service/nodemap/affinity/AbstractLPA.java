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

import com.sun.sgs.impl.service.nodemap.affinity.dlpa.AffinitySet;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.graph.dlpa.GraphBuilder;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract class implementing parts of the label propagation algorithm
 * used by both the single node and distributed versions.
 * <p>
 * The following property is supported:
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt>	<i>Property:</i> <code><b>
 *   com.sun.sgs.impl.service.nodemap.affinity.numThreads
 *	</b></code><br>
 *	<i>Default:</i>
 *    {@code 4}
 * <br>
 *
 * <dd style="padding-top: .5em">The number of threads to use while running
 *     the algorithm. Set to {@code 1} to run single-threaded.
 * <p>
 * </dl>
 * Set logging to Level.FINEST for a trace of the algorithm (very verbose
 * and slow).
 * Set logging to Level.FINER to see the final labeled graph.
 * Set logging to Level.FINE and construct with {@code gatherStats} set to
 *  {@code true} to print some high level statistics about each algorithm run.
 */
public abstract class AbstractLPA {
    /** Our package name. */
    protected static final String PKG_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** Our logger. */
    protected static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The property name for the number of threads to use. */
    public static final String NUM_THREADS_PROPERTY = PKG_NAME + ".numThreads";

    /** The default value for the number of threads to use. */
    public static final int DEFAULT_NUM_THREADS = 4;

    /** The producer of our graphs. */
    protected final GraphBuilder builder;

    /** The local node id. */
    protected final long localNodeId;

    /** A random number generator, to break ties. */
    protected final Random ran = new Random();

    /** Our executor, for running tasks in parallel. */
    protected final ExecutorService executor;

    /** The number of threads this algorithm should use. */
    protected final int numThreads;

    /** If true, gather statistics for each run. */
    protected final boolean gatherStats;
    /** The time spent in the last run, only valid if gatherStats is true. */
    protected long time;

    /**
     * The number of iterations required for the last run,
     * only valid if gatherStats is true.
     */
    protected int iterations;

    /** The graph in which we're finding communities.  This is a live
     * graph for some graph builders;  we have to be able to handle changes.
     */
    protected volatile UndirectedSparseGraph<LabelVertex, WeightedEdge> graph;

    /** For now, we're only grabbing the vertices of interest at the
     * start of the algorithm.   This could change so we update for each run,
     * but for now it's easiest to leave this list fixed.
     */
    protected volatile List<LabelVertex> vertices;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param nodeId the local node ID
     * @param properties the properties for configuring this service
     * @param gatherStats if {@code true}, gather extra statistics for each run.
     *            Useful for testing.
     *
     * @throws IllegalArgumentException if {@code numThreads} is
     *       less than {@code 1}
     * @throws Exception if any other error occurs
     */
    public AbstractLPA(GraphBuilder builder, long nodeId,
                            Properties properties,
                            boolean gatherStats)
        throws Exception
    {
        this.builder = builder;
        localNodeId = nodeId;
        this.gatherStats = gatherStats;

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        numThreads = wrappedProps.getIntProperty(
            NUM_THREADS_PROPERTY, DEFAULT_NUM_THREADS, 1, 65535);
        if (numThreads > 1) {
            executor = Executors.newFixedThreadPool(numThreads);
        } else {
            executor = null;
        }
    }

    /**
     * Initialize ourselves for a run of the algorithm.
     */
    protected void initializeLPARun() {
        logger.log(Level.FINEST, "{0}: initializing LPA run", localNodeId);
        // Grab the graph (the weighted graph builder returns a pointer
        // to the live graph) and a snapshot of the vertices.
        graph = builder.getAffinityGraph();
        assert (graph != null);

        // The set of vertices we iterate over is fixed (e.g. we don't
        // consider new vertices as we process this graph).  If processing
        // takes a long time, or if we use a more dynamic work queue, we'll
        // want to revisit this.
        Collection<LabelVertex> graphVertices = graph.getVertices();
        if (graphVertices == null) {
            vertices = new ArrayList<LabelVertex>();
        } else {
            vertices = new ArrayList<LabelVertex>(graphVertices);
        }

        // Initialize algorithm-specific info
        doOtherInitialization();
        logger.log(Level.FINEST,
                   "{0}: finished initializing LPA run", localNodeId);
    }

    /**
     * Perform any algorithm specific initialization for an algorithm run.
     */
    protected abstract void doOtherInitialization();
    /**
     * Sets the label of {@code vertex} to the label used most frequently
     * by {@code vertex}'s neighbors.  Returns {@code true} if {@code vertex}'s
     * label changed.
     *
     * @param vertex a vertex in the graph
     * @param self {@code true} if we should pick our own label if it is
     *             in the set of highest labels
     * @return {@code true} if {@code vertex}'s label is changed, {@code false}
     *        if it is not changed
     */
    protected boolean setMostFrequentLabel(LabelVertex vertex, boolean self) {
        List<Integer> highestSet = getNeighborCounts(vertex);

        // If we got back an empty set, no neighbors were found and we're done.
        if (highestSet.isEmpty()) {
            return false;
        }

        // If our current label is in the set of highest labels, we're done.
        if (self && highestSet.contains(vertex.getLabel())) {
            return false;
        }

        // Otherwise, choose a label at random
        vertex.setLabel(highestSet.get(ran.nextInt(highestSet.size())));
        logger.log(Level.FINEST, "{0} : Returning true: vertex is now {1}",
                                 localNodeId, vertex);
        return true;
    }

    /**
     * Given a graph, and a vertex within that graph, find the set of labels
     * with the highest count amongst {@code vertex}'s neighbors.
     *
     * @param vertex the vertex whose neighbors labels will be examined
     * @return a list of labels with the higest counts
     */
    protected List<Integer> getNeighborCounts(LabelVertex vertex) {
        // A map of labels -> counts, counting how many
        // of our neighbors use a particular label.
        Map<Integer, Long> labelMap = new HashMap<Integer, Long>();

        // Put our neighbors vertex into the map.  We allow parallel edges, and
        // use edge weights.
        // NOTE can remove some code if we decide we don't need parallel edges
        Collection<LabelVertex> neighbors = graph.getNeighbors(vertex);
        if (neighbors == null) {
            // JUNG returns null if vertex is not present
            return new ArrayList<Integer>();
        }

        StringBuffer logSB = new StringBuffer();
        for (LabelVertex neighbor : neighbors) {
            Integer label = neighbor.getLabel();
            Long value = labelMap.containsKey(label) ?
                            labelMap.get(label) : 0;
            WeightedEdge edge = graph.findEdge(vertex, neighbor);
            if (edge != null) {
                if (logger.isLoggable(Level.FINEST)) {
                    logSB.append(neighbor + "(" + edge.getWeight() + ") ");
                }
                value += edge.getWeight();
                labelMap.put(label, value);
            }
        }

        // Allow algorithms a shot at updating the labelMap.  In particular,
        // the distributed algorithm needs to update information based on
        // cache eviction data.
        doOtherNeighbors(vertex, labelMap, logSB);

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "{0}: Neighbors of {1} : {2}",
                       localNodeId, vertex, logSB.toString());
        }

        // Find the set of labels used the max number of times
        long maxValue = -1L;
        List<Integer> maxLabelSet = new ArrayList<Integer>();
        for (Map.Entry<Integer, Long> entry : labelMap.entrySet()) {
            long val = entry.getValue();
            if (val > maxValue) {
                maxValue = val;
                maxLabelSet.clear();
                maxLabelSet.add(entry.getKey());
            } else if (val == maxValue) {
                maxLabelSet.add(entry.getKey());
            }
        }
        return maxLabelSet;
    }

    /**
     * Update the label map with any other neighbors known to a
     * particular algorithm.
     * @param vertex the vertex whose neighbors labels will be examined
     * @param labelMap a map of labels to counts of neighbors using that label
     * @param logSB a StringBuffer for gathering log info about neighbors
     */
    protected abstract void doOtherNeighbors(LabelVertex vertex,
                                             Map<Integer, Long> labelMap,
                                             StringBuffer logSB);

    /**
     * Return the affinity groups found within the given vertices, putting all
     * nodes with the same label in a group.  The affinity group's id
     * will be the common label of the group.  Also, as an optimization,
     * can reinitialize the labels to their initial setting.
     *
     * @param vertices the vertices that we gather groups from
     * @param reinitialize if {@code true}, reinitialize the labels
     * @return the affinity groups
     */
    protected static Collection<AffinityGroup> gatherGroups(
            List<LabelVertex> vertices, boolean reinitialize)
    {
        assert (vertices != null);
        // All nodes with the same label are in the same community.
        Map<Integer, AffinityGroup> groupMap =
                new HashMap<Integer, AffinityGroup>();
        for (LabelVertex vertex : vertices) {
            int label = vertex.getLabel();
            AffinitySet ag =
                    (AffinitySet) groupMap.get(label);
            if (ag == null) {
                ag = new AffinitySet(label);
                groupMap.put(label, ag);
            }
            ag.addIdentity(vertex.getIdentity());
            if (reinitialize) {
                vertex.initializeLabel();
            }
        }
        return groupMap.values();
    }

    // Utility methods, mostly used for performance testing.
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

}
