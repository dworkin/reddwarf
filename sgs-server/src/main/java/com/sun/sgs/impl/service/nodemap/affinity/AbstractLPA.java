/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.NamedThreadFactory;
import edu.uci.ics.jung.graph.UndirectedGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
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
 * The logger for the affinity group finding system is named
 * {@value #PROP_NAME}.
 * <p>
 * Set logging to Level.FINEST for a trace of the algorithm (very verbose
 * and slow).
 * Set logging to Level.FINER to see the final labeled graph.
 * Set logging to Level.FINE for any errors or unexpected conditions encountered
 * during the run.
 */
public abstract class AbstractLPA extends BasicState {
    /** Our base property name. */
    protected static final String PROP_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** Our logger.  Note this is shared between graph builders and group
     * finders.
     */
    protected static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PROP_NAME));

    /** The property name for the number of threads to use. */
    public static final String NUM_THREADS_PROPERTY = PROP_NAME + ".numThreads";

    /** The default value for the number of threads to use. */
    public static final int DEFAULT_NUM_THREADS = 4;

    /** The local node id. */
    protected final long localNodeId;

    /** A random number generator, to break ties. */
    protected final Random ran = new Random();

    /** Our executor, for running tasks in parallel. */
    // TBD:  use taskScheduler?
    protected final ExecutorService executor;

    /** The number of threads this algorithm should use. */
    protected final int numThreads;

    /**  The number of iterations required for the last run. */
    protected int iterations;

    /** The graph in which we're finding communities.  This is a live
     * graph for some graph builders;  we have to be able to handle changes.
     */
    protected volatile UndirectedGraph<LabelVertex, WeightedEdge> graph;

    /** For now, we're only grabbing the vertices of interest at the
     * start of the algorithm.   This could change so we update for each run,
     * but for now it's easiest to leave this list fixed.
     */
    protected volatile List<LabelVertex> vertices;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param nodeId the local node ID
     * @param properties the properties for configuring this service
     *
     * @throws IllegalArgumentException if {@code numThreads} is
     *       less than {@code 1}
     * @throws Exception if any other error occurs
     */
    public AbstractLPA(long nodeId, Properties properties)
        throws Exception
    {
        localNodeId = nodeId;

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        numThreads = wrappedProps.getIntProperty(
            NUM_THREADS_PROPERTY, DEFAULT_NUM_THREADS, 1, 65535);
        if (numThreads > 1) {
            executor = Executors.newFixedThreadPool(numThreads,
                    new NamedThreadFactory("LPA"));
        } else {
            executor = null;
        }
        logger.log(Level.CONFIG,
                       "Creating LPA with properties:" +
                       "\n  " + NUM_THREADS_PROPERTY + "=" + numThreads);
    }

    /**
     * Initialize ourselves for a run of the algorithm.
     * @param builder the graph producer
     */
    protected void initializeLPARun(AffinityGraphBuilder builder) {
        logger.log(Level.FINEST, "{0}: initializing LPA run", localNodeId);
        // Grab the graph and a snapshot of the vertices.

        // Most graph builders return a pointer to the "live" graph,
        // but the BipartiteGraphBuilder constructs the graph on the fly
        // with each call.   As a result, graph cannot simply be a final field.
        // Additionally, getAffinityGraph should only be called ONCE per
        // alogorithm run, or we'll lose the labels when the graph is rebuilt.
        graph = builder.getAffinityGraph();
        assert (graph != null);

        // The set of vertices we iterate over is fixed (e.g. we don't
        // consider new vertices as we process this graph).  If processing
        // takes a long time, or if we use a more dynamic work queue, we'll
        // want to revisit this.
        // Note that there is no guarantee that the set of vertices represents
        // different identities on each node (we could be unlucky and have
        // an identity move to a new node while each node takes this snapshot).
        // There is no guarantee that, in a given set of affinity groups, each
        // identity exists in only one group.
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
        List<Integer> highestSet = getMaxCountLabels(vertex);

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
     * @return an unmodifiable list of labels with the highest counts
     */
    private List<Integer> getMaxCountLabels(LabelVertex vertex) {
        // Get the neighbor edges.
        Collection<WeightedEdge> edges = graph.getIncidentEdges(vertex);
        if (edges == null) {
            // JUNG returns null if vertex is not present; this can occur
            // if our graph was pruned while the algorithm is running
            return Collections.emptyList();
        }

        // A map of labels -> counts, counting how many
        // of our neighbors use a particular label.
        Map<Integer, Long> labelMap = new HashMap<Integer, Long>(edges.size());

        // Put our neighbors labels into the label map.  We assume there
        // are no parallel edges, but edges will have weights.
        //
        // As we iterate, calculate the maximum count of any particular label
        // for use later
        long maxCount = -1L;
        StringBuilder logSB = new StringBuilder();     // for logging
        for (WeightedEdge edge : edges) {
            LabelVertex neighbor = graph.getOpposite(vertex, edge);
            Integer label = neighbor.getLabel();
            Long value = labelMap.containsKey(label) ? labelMap.get(label) : 0;
            if (logger.isLoggable(Level.FINEST)) {
                logSB.append(neighbor + "(" + edge.getWeight() + ") ");
            }
            value += edge.getWeight();
            labelMap.put(label, value);
            if (value > maxCount) {
                maxCount = value;
            }
        }

        // Allow algorithms a shot at updating the labelMap.  In particular,
        // the distributed algorithm needs to update information based on
        // cache eviction data.
        long maxOtherCount = doOtherNeighbors(vertex, labelMap, logSB);

        if (maxOtherCount > maxCount) {
            maxCount = maxOtherCount;
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "{0}: Neighbors of {1} : {2}",
                       localNodeId, vertex, logSB.toString());
        }

        // Find the set of labels used the max number of times
        List<Integer> maxLabelList = new ArrayList<Integer>();
        for (Map.Entry<Integer, Long> entry : labelMap.entrySet()) {
            if (entry.getValue() == maxCount) {
                maxLabelList.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(maxLabelList);
    }

    /**
     * Update the label map with any other neighbors known to a
     * particular algorithm.
     * @param vertex the vertex whose neighbors labels will be examined
     * @param labelMap a map of labels to counts of neighbors using that label
     * @param logSB a StringBuilder for gathering log info about neighbors
     * @return the highest number of times a particular label is used among the
     *        other neighbors, or {@code -1L} if there are no other neighbors.
     */
    protected abstract long doOtherNeighbors(LabelVertex vertex,
                                             Map<Integer, Long> labelMap,
                                             StringBuilder logSB);

    /**
     * Return the affinity groups found within the given vertices, putting all
     * vertices with the same label in a group.  The affinity group's id
     * will be the common label of the group.  As an optimization, this method
     * can reinitialize the labels in the graph to their initial setting. Each
     * affinity group in the returned set will have the same generation number,
     * which will be {@code gen}.
     * <p>
     * @param vertices the vertices that we gather groups from
     * @param reinitialize if {@code true}, reinitialize the labels
     * @param gen the generation number
     * @return an unmodifiable set of affinity groups found in the graph
     */
    protected static Set<AffinityGroup> gatherGroups(
            List<LabelVertex> vertices, boolean reinitialize, long gen)
    {
        assert (vertices != null);
        // All nodes with the same label are in the same community.
        Map<Integer, AffinitySet> groupMap =
                new HashMap<Integer, AffinitySet>();
        for (LabelVertex vertex : vertices) {
            int label = vertex.getLabel();
            AffinitySet ag = groupMap.get(label);
            if (ag == null) {
                ag = new AffinitySet(label, gen, vertex.getIdentity());
                groupMap.put(label, ag);
            } else {
                ag.addIdentity(vertex.getIdentity());
            }
            if (reinitialize) {
                // At the end of an algorithm run, we save a pass through
                // all vertices in the graph if we reinitialize the vertices
                // while we gather the final groups.
                vertex.initializeLabel();
            }
        }
        return Collections.unmodifiableSet(
                new HashSet<AffinityGroup>(groupMap.values()));
    }
}
