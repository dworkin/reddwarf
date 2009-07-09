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
import com.sun.sgs.impl.util.Exporter;
import edu.uci.ics.jung.graph.Graph;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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
public class LabelPropagation implements LPAClient {
    private static final String PKG_NAME = 
            "com.sun.sgs.impl.service.nodemap.affinity";
    // Our logger
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME));

    // The producer of our graphs.
    private final GraphBuilder builder;

    // The local node id
    private final long localNodeId;

    // The server : our master
    private final LPAServer server;
    // A map of cached nodeId->LPAClient
    private final Map<Long, LPAClient> nodeProxies = new
            ConcurrentHashMap<Long, LPAClient>();

    // The exporter
    private final Exporter<LPAClient> clientExporter;

    // A random number generator, to break ties.
    private final Random ran = new Random();

    // Our executor, for running tasks in parallel.
    private final ExecutorService executor;

    // The number of threads this algorithm should use.
    private final int numThreads;

    // The node preference factor.  Zero means no node preference, a small
    // positive number means a slight preference to nodes with higher degrees,
    // and a small negative number means a slight preference to nodes with
    // lower degrees.
    // See Towards Real-Time Community Detection in Large Networks, 2009,
    // Leung, Hui, Lio, Crowcroft.
    private final double nodePref;

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
    // For now, we're only grabbing the vertices of interest at the
    // start of the algorithm.  This will change. JANE
    private List<LabelVertex> vertices;

    // The map of objects to node IDs, and a weight, which we get from
    // the graph builder at the start of an algorithm run.  We assign
    // a map to it here just to ensure that we never see a null value.
    private Map<Object, Map<Long, Integer>> conflictMap =
            new ConcurrentHashMap<Object, Map<Long, Integer>>();

    // The map of identities to node IDs and a weight.
    private Map<Identity, Map<Long, Integer>> remoteNeighborMap =
            new ConcurrentHashMap<Identity, Map<Long, Integer>>();

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param nodeId the local node ID
     * @param host the server host name
     * @param port the port used by the LPAServer
     * @param gatherStats if {@code true}, gather extra statistics for each run.
     *            Useful for testing.
     * @param numThreads number of threads, for TESTING.
     *      If 1, use the sequential asynchronous version.
     *      If >1, use the parallel version, with that number of threads.
     * @param nodePref node preference factor
     *
     * @throws IllegalArgumentException if {@code numThreads} is
     *       less than {@code 1}
     * @throws Exception if any other error occurs
     */
    public LabelPropagation(GraphBuilder builder, long nodeId,
                            String host, int port,
                            boolean gatherStats,
                            int numThreads, double nodePref)
        throws Exception
    {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Num threads must be > 0");
        }
        this.builder = builder;
        localNodeId = nodeId;
        this.gatherStats = gatherStats;
        this.numThreads = numThreads;
        if (numThreads > 1) {
            executor = Executors.newFixedThreadPool(numThreads);
        } else {
            executor = null;
        }
        this.nodePref = nodePref;

        // Look up our server
        Registry registry = LocateRegistry.getRegistry(host, port);
        server = (LPAServer) registry.lookup(
                         LabelPropagationServer.SERVER_EXPORT_NAME);
        // Export ourselves using anonymous ports, and register with server
        // Do we want to combine these 2 interfaces?  Most likely.
        // Do we want to combine this with the NMS client?  I doubt it.
        // Another option is to have the LPAServer collect and exchange
        // all cross node edge info, and the remote labels at the start
        // of each iteration.  That would be helpful, because then the
        // server knows when all preliminary information has been exchanged.
        clientExporter = new Exporter<LPAClient>(LPAClient.class);
        clientExporter.export(this, 0);

        server.register(nodeId, clientExporter.getProxy());
    }
    
    // --- implement LPAClient -- //
    /** {@inheritDoc} */
    public Collection<AffinityGroup> affinityGroups() throws IOException {
        return groups;
    }

    /** {@inheritDoc} */
    public void exchangeCrossNodeInfo() throws IOException {
        // Get conflict information from the graph builder
        conflictMap = builder.getConflictMap();

        // Go through the map, gathering together all obj ids for a single
        // node.  JANE perhaps we should allow the builder to return this
        // view, as well, and maintain it as the info comes in?  That means
        // more work for the pruner, though.
        Map<Long, Set<Object>> nodeConflictMap =
                new HashMap<Long, Set<Object>>();
        for (Map.Entry<Object, Map<Long, Integer>> entry:
             conflictMap.entrySet())
        {
            Object objId = entry.getKey();
            for (Long nodeId : entry.getValue().keySet()) {
                Set<Object> idSet = nodeConflictMap.get(nodeId);
                if (idSet == null) {
                    idSet = new HashSet<Object>();
                    nodeConflictMap.put(nodeId, idSet);
                }
                idSet.add(objId);
            }
        }

        // Now, go through the new map, and tell each node about the
        // edges we might have in common.
        for (Map.Entry<Long, Set<Object>> entry : nodeConflictMap.entrySet()) {
            // JANE is it safe to make a remote call from a remote call?
            // Are there any timing errors or deadlock conditions I should
            // think about?
            Long nodeId = entry.getKey();
            LPAClient proxy = nodeProxies.get(nodeId);
            if (proxy == null) {
                // Ask the server for it. Retries?
                proxy = server.getLPAClientProxy(nodeId);
                if (proxy != null) {
                    nodeProxies.put(nodeId, proxy);
                }
            }
            // Tell the other node about the conflicts we know of.
            // JANE should this also include weights?  I think so,
            // so both sides are using the same info
            if (proxy != null) {
                proxy.crossNodeEdges(entry.getValue(), localNodeId);
            }
        }
    }

    /** {@inheritDoc} */
    public void removeNode(long nodeId) throws IOException {
        nodeProxies.remove(nodeId);
    }

    /** {@inheritDoc} */
    public void startIteration(int iteration) throws IOException {
        long startTime = System.currentTimeMillis();
        // Step 1.  Initialize all nodes in the network.
        //          Their labels are their Identities.
        //          Note that labels are initialized to the Identity when
        //          a node is first created, and reinitialized when we
        //          since we know we're in the first iteration.
        if (iteration == 1) {
            // The set of vertices we iterate over is fixed (e.g. we don't
            // consider new vertices as we process this graph).  If processing
            // takes a long time, or if we use a more dynamic work queue, we'll
            // want to revisit this.
            graph = createLabelGraph();
            vertices = new ArrayList<LabelVertex>(graph.getVertices());
        }

//        // Step 2.  Set t = 1;
//        int t = 1;

        // Exchange label information with remote nodes.
      // XXX findRemoteNeighbors and get their information.
//        while (true) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "GRAPH at iteration {0} is {1}",
                                          iteration, graph);
            }
            // Step 3.  Arrange the nodes in a random order and set it to X.
            // Step 4.  For each vertices in X chosen in that specific order,
            //          let the label of vertices be the label of the highest
            //          frequency of its neighbors.
            boolean changed = false;

            // Choose a different ordering for each iteration
            if (iteration > 1) {
                Collections.shuffle(vertices);
            }

            if (numThreads > 1) {
                final AtomicBoolean abool = new AtomicBoolean(false);
                List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
                for (final LabelVertex vertex : vertices) {
                    tasks.add(new Callable<Void>() {
                        public Void call() {
                            abool.set(setMostFrequentLabel(vertex) ||
                                      abool.get());
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
                                    " during iteration " + iteration);
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
//            if (!changed) {
//                break;
//            }
//            t++;

            if (logger.isLoggable(Level.FINEST)) {
                // Log the affinity groups so far:
                Collection<AffinityGroup> intermediateGroups =
                        gatherGroups(vertices, false);
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

            // Tell the server we've finished this iteration
            server.finishedIteration(localNodeId, !changed, iteration);
//        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "FINAL GRAPH IS {0}", graph);
        }
        groups = gatherGroups(vertices, true);

        if (gatherStats) {
            // Record our statistics for this run, used for testing.
            time = System.currentTimeMillis() - startTime;
            iterations = iteration;
            // Note that the graph might be changing while we ran
            // the algorithm.
            modularity = Graphs.calcModularity(graph, groups);

            if (logger.isLoggable(Level.FINE)) {
                StringBuffer sb = new StringBuffer();
                sb.append(" LPA (" + numThreads + ") took " +
                          time + " milliseconds, " +
                          iterations + " iterations, and found " +
                          groups.size() + " groups ");
                sb.append(" modularity " + modularity);
                for (AffinityGroup group : groups) {
                    sb.append(" id: " + group.getId() + ": members ");
                    for (Identity id : group.getIdentities()) {
                        sb.append(id + " ");
                    }
                }
                logger.log(Level.FINE, sb.toString());
            }
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** {@inheritDoc} */
    public void crossNodeEdges(Collection<Object> objIds, long nodeId) 
            throws IOException
    {
        /// hmmm... this is just an update conflict information without the
        // prune stuff...
        for (Object objId : objIds) {
            Map<Long, Integer> nodeMap = conflictMap.get(objId);
            if (nodeMap == null) {
                nodeMap = new ConcurrentHashMap<Long, Integer>();
            }
            int value = nodeMap.containsKey(nodeId) ? nodeMap.get(nodeId) : 0;
            value++;
            conflictMap.put(objId, nodeMap);
        }
    }

    /** {@inheritDoc} */
    public Map<Object, Set<Integer>> getRemoteLabels(Collection<Object> objIds) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Using the filled in conflict information, set up our map of identities
     * to remote neighbors.
     */
    private void findRemoteNeighbors() {
        // JANE better to do this while gathering up map info and
        // sending it around, and fixing up results??
        Map<Object, Map<Identity, Integer>> objectMap = 
                                    builder.getObjectUseMap();
        // For each entry in the conflict map
        for (Map.Entry<Object, Map<Long, Integer>> entry: 
             conflictMap.entrySet())
        {
            Object objId = entry.getKey();
            // Find the identities using the object
            Map<Identity, Integer> objUseIdentities = objectMap.get(objId);
            if (objUseIdentities == null) {
                break;
            }
            Map<Long, Integer> nodes = entry.getValue();
            // For each identity using the object, find the remote nodes which
            // are neighbors for that object.
            for (Map.Entry<Identity, Integer> idEntry : 
                 objUseIdentities.entrySet())
            {
                Identity id = idEntry.getKey();
                Map<Long, Integer> remoteIdMap = remoteNeighborMap.get(id);
                if (remoteIdMap == null) {
                    remoteIdMap = new ConcurrentHashMap<Long, Integer>();
                    remoteNeighborMap.put(id, remoteIdMap);
                }
                
                for (Map.Entry<Long, Integer> nodeEntries : nodes.entrySet()) {
                    Long nodeId = nodeEntries.getKey();
                    Integer value = remoteIdMap.get(nodeId);
                    int val = (value == null) ? 0 : value;
                    remoteIdMap.put(nodeId, val + nodeEntries.getValue());
                }
            }
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

        // The set of vertices we iterate over is fixed (e.g. we don't
        // consider new vertices as we process this graph).  If processing
        // takes a long time, or if we use a more dynamic work queue, we'll
        // want to revisit this.
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
                            abool.set(setMostFrequentLabel(vertex) ||
                                      abool.get());
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
                        gatherGroups(vertices, false);
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
        groups = gatherGroups(vertices, true);

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
                sb.append(" modularity " + modularity);
                for (AffinityGroup group : groups) {
                    sb.append(" id: " + group.getId() + ": members ");
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
        clientExporter.unexport();
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
        List<Integer> highestSet = getNeighborCounts(node);

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
    private List<Integer> getNeighborCounts(LabelVertex node) {
        // A map of labels -> value, effectively counting how many
        // of our neighbors use a particular label.
        Map<Integer, Double> labelMap = new HashMap<Integer, Double>();

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
            Double value = labelMap.containsKey(label) ?
                         labelMap.get(label) : 0.0;
            // Use findEdgeSet to allow parallel edges
            Collection<WeightedEdge> edges = graph.findEdgeSet(node, neighbor);
            // edges will be null if node and neighbor are no longer connected;
            // in that case, do nothing
            if (edges != null) {
                long edgew = 0;
                for (WeightedEdge edge : edges) {
                    edgew += edge.getWeight();
                }
                // Using node preference alone causes the single threaded
                // version to drop quite a bit for Zachary and a value of
                // 0.1 or 0.2, and nice modularity boost at -0.1
//                value += Math.pow(graph.degree(neighbor), nodePref) * edgew;
                value += edgew;
                labelMap.put(label, value);
            }
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Neighbors of {0} : {1}",
                       node, logSB.toString());
        }

        double maxValue = -1.0;
        List<Integer> maxLabelSet = new ArrayList<Integer>();
        for (Map.Entry<Integer, Double> entry : labelMap.entrySet()) {
            double val = entry.getValue();
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
     * Return the affinity groups found within the given vertices, putting all
     * nodes with the same label in a group.  The affinity group's id
     * will be the common label of the group.
     *
     * @param vertices the vertices that we gather groups from
     * @param clean if {@code true}, reinitialize the labels
     * @return the affinity groups
     */
    private Collection<AffinityGroup> gatherGroups(List<LabelVertex> vertices,
                                                   boolean clean)
    {
        // All nodes with the same label are in the same community.
        Map<Integer, AffinityGroup> groupMap =
                new HashMap<Integer, AffinityGroup>();
        for (LabelVertex vertex : vertices) {
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
