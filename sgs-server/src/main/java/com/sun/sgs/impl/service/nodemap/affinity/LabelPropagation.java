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
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.GraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.util.Exporter;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A distributed implementation of the algorithm presented in
 * "Near linear time algorithm to detect community structures in large-scale
 * networks" by U.N. Raghavan, R. Albert and S. Kumara 2007
 * <p>
 * This class supports running on a single node (useful for testing
 * algorithm changes) and in a distributed system on several nodes.
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
    private final Map<Long, LPAClient> nodeProxies = 
            new ConcurrentHashMap<Long, LPAClient>();

    // The exporter
    private final Exporter<LPAClient> clientExporter;

    // A random number generator, to break ties.
    private final Random ran = new Random();

    // Our executor, for running tasks in parallel.
    private final ExecutorService executor;

    // The number of threads this algorithm should use.
    private final int numThreads;

    // If true, gather statistics for each run.
    private final boolean gatherStats;
    // For single node performance testing, only valid if gatherStats is true.
    private long time;
    private int iterations;
    private double modularity;

    // The graph in which we're finding communities.  This is a live
    // graph for some graph builders;  we have to be able to handle changes.
    private volatile UndirectedSparseGraph<LabelVertex, WeightedEdge> graph;

    // For now, we're only grabbing the vertices of interest at the
    // start of the algorithm.   This could change so we update for each run,
    // but for now it's easiest to leave this list fixed.
    private volatile List<LabelVertex> vertices;

    // Lock to ensure we aren't modifying the vertices list at the same
    // time we're processing an asynchronous call from another node.
    private final Object verticesLock = new Object();

    // The map of conflicts in the system, nodeId->objId, count
    // Updates are multi-threaded.
    private final ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
        nodeConflictMap =
            new ConcurrentHashMap<Long, ConcurrentMap<Object, AtomicLong>>();

    // Map identity -> label and count
    // This sums all uses of that identity on other nodes. The count takes
    // weights for object uses into account.
    // Updates are currently single threaded, node by node.
    private final ConcurrentMap<Identity, Map<Integer, Long>>
        remoteLabelMap =
            new ConcurrentHashMap<Identity, Map<Integer, Long>>();

    // Synchronization for state, runNumber, and iteration
    private final Object stateLock = new Object();

    // States of this instance, ensuring that calls from the server are
    // idempotent
    private enum State {
        // Preparing for an algorithm run
        PREPARING,
        // In the midst of an iteration
        IN_ITERATION,
        // Gathering up the final groups
        GATHERING_GROUPS,
            // Completed gathering groups
            GATHERED_GROUPS,
        // Idle (none of the above)
        IDLE
    }

    /** The current state of this instance. */
    private State state = State.IDLE;

    // The current algorithm run number, used to ensure we're returning
    // values for the correct algorithm run.
    private volatile long runNumber = -1;

    // The current iteration being run, used to detect multiple calls
    // for an iteration.
    private volatile int iteration = -1;

    // The groups collected in the last run
    private Collection<AffinityGroup> groups;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param nodeId the local vertex ID
     * @param host the server host name
     * @param port the port used by the LPAServer
     * @param gatherStats if {@code true}, gather extra statistics for each run.
     *            Useful for testing.
     * @param numThreads number of threads, for TESTING.
     *      If 1, use the sequential asynchronous version.
     *      If >1, use the parallel version, with that number of threads.
     *
     * @throws IllegalArgumentException if {@code numThreads} is
     *       less than {@code 1}
     * @throws Exception if any other error occurs
     */
    public LabelPropagation(GraphBuilder builder, long nodeId,
                            String host, int port,
                            boolean gatherStats,
                            int numThreads)
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

        // Look up our server
        Registry registry = LocateRegistry.getRegistry(host, port);
        server = (LPAServer) registry.lookup(
                         LabelPropagationServer.SERVER_EXPORT_NAME);
        // Export ourselves using an anonymous port, and register with server
        // Another option is to have the LPAServer collect and exchange
        // all cross node edge info, and the remote labels at the start
        // of each iteration.  That would be helpful, because then the
        // server knows when all preliminary information has been exchanged.
        clientExporter = new Exporter<LPAClient>(LPAClient.class);
        int exportPort = clientExporter.export(this, 0);
        server.register(nodeId, clientExporter.getProxy());
        if (logger.isLoggable(Level.CONFIG)) {
            logger.log(Level.CONFIG, "Created label propagation node on {0} " +
                    " using server on {1}:{2}, and exported self on {3}",
                    localNodeId, host, port, exportPort);
        }
    }
    
    // --- implement LPAClient -- //
    
    /** {@inheritDoc} */
    public Collection<AffinityGroup> getAffinityGroups(long runNumber,
                                                       boolean done)
        throws IOException
    {
        synchronized (stateLock) {
            if (this.runNumber != runNumber) {
                throw new IllegalArgumentException(
                    "bad run number " + runNumber +
                    ", expected " + this.runNumber);
            }
            if (done) {
                // If done is true, we will be initializing the graph for
                // our next iteration as we gather the final group data,
                // making it impossible for us to gather the data again.
                while (state == State.GATHERING_GROUPS) {
                    try {
                        stateLock.wait();
                    } catch (InterruptedException e) {
                        // Do nothing - ignore until state changes
                    }
                }
                if (state == State.GATHERED_GROUPS) {
                    // We have collected data for this run already, just return
                    // them.
                    logger.log(Level.FINE,
                            "{0}: returning {1} precalculated groups",
                            localNodeId, groups.size());
                    new HashSet<AffinityGroup>(groups);
                }
                state = State.GATHERING_GROUPS;
            }
        }

        // Log the final graph before calling gatherGroups, which is
        // also responsible for reinitializing the vertices for the next run
        // if done is true
        if (done && logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "{0}: FINAL GRAPH IS {1}",
                                     localNodeId, graph);
        }
        groups = gatherGroups(vertices, done);

        if (done) {
            // Clear our maps that are set up as the first step of an
            // algorithm run.  Doing this here ensures we catch protocol
            // errors, where we attempt to run iterations before we've
            // prepared for a run.
            nodeConflictMap.clear();
            vertices = null;

            synchronized (stateLock) {
                state = State.GATHERED_GROUPS;
                stateLock.notifyAll();
            }
        }
        logger.log(Level.FINEST, "{0}: returning {1} groups",
                   localNodeId, groups.size());
        return new HashSet<AffinityGroup>(groups);
    }

    /** 
     * {@inheritDoc}
     * <p>
     * Asynchronously prepare ourselves for a run.
     */
    public void prepareAlgorithm(long runNumber) throws IOException {
        PrepareRun pr = new PrepareRun(runNumber);
        String name = "PrepareAlgorithm-" + runNumber;
        new Thread(pr, name).start();
    }

    private class PrepareRun implements Runnable {
        final long run;
        PrepareRun(long run) {
            this.run = run;
        }
        public void run() {
            prepareAlgorithmInternal(run);
        }

    }

    /**
     * Prepare for an algorithm run.  If we are unable to contact the server
     * to report we are finished, log the failure and request that this
     * node be shut down.  JANE TODO
     * <p>
     * Each pair of nodes needs to exchange conflict information to ensure
     * that both pairs know the complete set for both (e.g. if node 1 has a
     * data conflict on obj1 with node 2, it lets node 2 know.
     * <p>
     * It might be better to just let the server ask each vertex for its
     * information and merge it there.
     *
     * @param runNumber
     */
    private void prepareAlgorithmInternal(long runNumber) {
        synchronized (stateLock) {
            if (runNumber == this.runNumber) {
                // We assume this happened if the server called us twice
                // due to IO retries.
                return;
            }
            while (state == State.PREPARING) {
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    // Do nothing - ignore until state changes
                }
            }
            if (this.runNumber > runNumber) {
                // Things are confused;  we should have already performed
                // the run.  Do nothing.
                logger.log(Level.FINE,
                            "{0}: bad run number {1}, " +
                            " we are on run {2}.  Returning.",
                            localNodeId, runNumber, this.runNumber);
                return;
            }
            this.runNumber = runNumber;
            iteration = -1;
            state = State.PREPARING;
        }

        initializeLPARun();
        initializeNodeConflictMap();

        // If we cannot reach a proxy, we invalidate the run.
        boolean failed = false;
        // Now, go through the new map, and tell each vertex about the
        // edges we might have in common.
        assert (nodeConflictMap != null);
        for (Map.Entry<Long, ConcurrentMap<Object, AtomicLong>> entry :
             nodeConflictMap.entrySet())
        {
            Long nodeId = entry.getKey();
            LPAClient proxy = getProxy(nodeId);

            if (proxy != null) {
                logger.log(Level.FINEST, "{0}: exchanging edges with {1}",
                           localNodeId, nodeId);
                Map<Object, AtomicLong> map = entry.getValue();
                assert (map != null);
                try {
                    proxy.crossNodeEdges(new HashSet<Object>(map.keySet()),
                                         localNodeId);
                } catch (IOException e) {
                    // JANE retry
                    failed = true;
                }
            } else {
                logger.log(Level.FINE, "{0}: could not exchange edges with {1}",
                           localNodeId, nodeId);
                failed = true;
                break;
            }
        }

        // Tell the server we're ready for the iterations to begin
        try {
            server.readyToBegin(localNodeId, failed);
        } catch (IOException e) {
            // JANE retry
            logger.logThrow(Level.WARNING, e,
                            "{0}: could not contact server", localNodeId);
        }
        synchronized (stateLock) {
            state = State.IDLE;
            stateLock.notifyAll();
        }
    }

    /** {@inheritDoc} */
    public void crossNodeEdges(Collection<Object> objIds, long nodeId)
        throws IOException
    {
        if (objIds == null) {
            // This is unexpected;  the other node should have returned
            // an empty collection.
            logger.log(Level.FINE, "unexpected null objIds");
            return;
        }
        ConcurrentMap<Object, AtomicLong> conflicts =
                nodeConflictMap.get(nodeId);
        if (conflicts == null) {
            ConcurrentMap<Object, AtomicLong> newConf =
                    new ConcurrentHashMap<Object, AtomicLong>();
            conflicts = nodeConflictMap.putIfAbsent(nodeId, newConf);
            if (conflicts == null) {
                conflicts = newConf;
            }
        }

        for (Object objId : objIds) {
            // Just the original value or 1
            // If we start using the number of conflicts, this might change.
            conflicts.putIfAbsent(objId, new AtomicLong(1));
        }
    }

    /** {@inheritDoc} */
    public void removeNode(long nodeId) throws IOException {
        removeNodeInternal(nodeId);
    }

    /**
     * Remove a failed node, telling dependent objects to do the same.
     * This method is not called remotely.
     * @param nodeId the ID of the failed node.
     */
    private void removeNodeInternal(long nodeId) {
        logger.log(Level.FINEST, "{0}: Removing node {1} from LPA",
                   localNodeId, nodeId);
        nodeProxies.remove(nodeId);
        nodeConflictMap.remove(nodeId);
        builder.removeNode(nodeId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method is run asynchronously.
     */
    public void startIteration(int iteration) throws IOException {
        IterationRun ir = new IterationRun(iteration);
        String name = "StartIteration-" + iteration;
        new Thread(ir, name).start();
    }

    private class IterationRun implements Runnable {
        final int iter;
        IterationRun(int iter) {
            this.iter = iter;
        }
        public void run() {
            startIterationInteral(iter);
        }
    }

    /**
     * Run an iteration of the algorithm.
     * <p>
     * If we are unable to contact the server to report we are finished,
     * log the failure and request that this node be shut down.  JANE TODO
     * @param iteration the iteration to run
     */
    private void startIterationInteral(int iteration) {
        // We should have been prepared by now.
        assert (vertices != null);
        long startTime = System.currentTimeMillis();
       
        // Block any additional threads entering this iteration
        synchronized (stateLock) {
            if (this.iteration == iteration) {
                // We have been called more than once by the server. Assume this
                // is due to IO retries, so no action is needed.
                return;
            }
            while (state == State.IN_ITERATION) {
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    // Do nothing - ignore until state changes
                }
            }
            if (this.iteration > iteration) {
                // Things are confused;  we should have already performed
                // the iteration.  Do nothing.
                logger.log(Level.FINE,
                            "{0}: bad iteration number {1}, " +
                            " we are on iteration {2}.  Returning.",
                            localNodeId, iteration, this.iteration);
                return;
            }
            // Record the current iteration so we can use it for error checks.
            this.iteration = iteration;
            state = State.IN_ITERATION;
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "{0}: GRAPH at iteration {1} is {2}",
                                      localNodeId, iteration, graph);
        }

        // Gather the remote labels from each node.
        boolean failed = updateRemoteLabels();

        // We include the current label when calculating the most frequent
        // label, so no labels changing indicates the algorithm has converged
        // and we can stop.
        boolean changed = false;

        if (!failed) {
            // Arrange the vertices in a random order for each iteration.
            // For the first iteration, we just use the iterator ordering.
            if (iteration > 1) {
                synchronized (verticesLock) {
                    Collections.shuffle(vertices);
                }
            }

            // For each of the vertices, set the label to the label with the
            // highest frequency of its neighbors.
            if (numThreads > 1) {
                final AtomicBoolean abool = new AtomicBoolean(false);
                List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
                for (final LabelVertex vertex : vertices) {
                    tasks.add(new Callable<Void>() {
                        public Void call() {
                            abool.set(setMostFrequentLabel(vertex, true) ||
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
                    failed = true;
                    logger.logThrow(Level.INFO, ie,
                                    " during iteration " + iteration);
                }
                changed = abool.get();

            } else {
                for (LabelVertex vertex : vertices) {
                    changed = setMostFrequentLabel(vertex, true) || changed;
                }
            }

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
                               "{0}: Intermediate group {1} , members: {2}",
                               localNodeId, group, logSB.toString());
                }
            }
        }
        // Tell the server we've finished this iteration
        // JANE need retry
        try {
            server.finishedIteration(localNodeId, !changed, failed, iteration);
        } catch (IOException e) {
            logger.logThrow(Level.WARNING, e,
                            "{0}: could not contact server", localNodeId);
        }

        synchronized (stateLock) {
            state = State.IDLE;
            stateLock.notifyAll();
        }
        if (gatherStats) {
            // Record our statistics for this run, used for testing.
            time = System.currentTimeMillis() - startTime;
            if (logger.isLoggable(Level.FINE)) {
                StringBuffer sb = new StringBuffer();
                sb.append("(" + localNodeId + ")");
                sb.append(" LPA (" + numThreads + ") took " +
                          time + " milliseconds");
                logger.log(Level.FINE, sb.toString());
            }
        }
    }

    /** {@inheritDoc} */
    public Map<Object, Map<Integer, List<Long>>> getRemoteLabels(
                Collection<Object> objIds)
            throws IOException
    {
        Map<Object, Map<Integer, List<Long>>> retMap =
                new HashMap<Object, Map<Integer, List<Long>>>();
        if (objIds == null) {
            // This is unexpected;  the other node should have passed in an
            // empty collection
            logger.log(Level.FINE, "unexpected null objIds");
            return retMap;
        }
        
        ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>> objectMap =
                builder.getObjectUseMap();
        assert (objectMap != null);

        synchronized (verticesLock) {
            for (Object obj : objIds) {
                // look up the set of identities
                ConcurrentMap<Identity, AtomicLong> idents = objectMap.get(obj);
                Map<Integer, List<Long>> labelWeightMap =
                        new HashMap<Integer, List<Long>>();
                // If idents is null, the identity is no longer used, probably
                // because the graph was pruned (we are using a live object
                // use map).
                if (idents != null) {
                    for (Map.Entry<Identity, AtomicLong> entry :
                        idents.entrySet())
                    {
                        // Find the label associated with the identity in
                        // the graph.
                        // We do this by creating vid, a template of the
                        // LabelVertex, and then finding the actual graph
                        // vertex with that identity.  The current label can
                        // be found in the actual graph vertex.
                        LabelVertex vid = new LabelVertex(entry.getKey());
                        int index = vertices.indexOf(vid);
                        if (index != -1) {
                            // If the vid wasn't found in the vertices list,
                            // it is a new identity since the vertices were
                            // captured at the start of this algorithm run,
                            // and we just ignore the label.
                            // Otherwise, add the label to set of labels for
                            // this identity.
                            Integer label = vertices.get(index).getLabel();

                            List<Long> weightList = labelWeightMap.get(label);
                            if (weightList == null) {
                                weightList = new ArrayList<Long>();
                                labelWeightMap.put(label, weightList);
                            }
                            weightList.add(entry.getValue().get());
                        }
                    }
                }
                retMap.put(obj, labelWeightMap);
            }
        }
        return retMap;
    }
    
    /** {@inheritDoc} */
    public void shutdown() {
        clientExporter.unexport();
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * Exchanges information with other nodes in the system to fill in the
     * remoteLabelMap.
     * @return {@code true} if a problem occurred
     */
    private boolean updateRemoteLabels() {
        // reinitialize the remote label map
        remoteLabelMap.clear();
        ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>> objectMap =
                builder.getObjectUseMap();
        assert (objectMap != null);
        
        boolean failed = false;

        // Now, go through the new map, asking for its labels
        assert (nodeConflictMap != null);
        for (Map.Entry<Long, ConcurrentMap<Object, AtomicLong>> entry :
             nodeConflictMap.entrySet())
        {
            Long nodeId = entry.getKey();
            LPAClient proxy = getProxy(nodeId);
            if (proxy == null) {
                logger.log(Level.FINE,
                          "{0}: could not exchange edges with {1}",
                          localNodeId, nodeId);
                failed = true;
                break;
            }

            // Tell the other vertex about the conflicts we know of.
            Map<Object, AtomicLong> map = entry.getValue();
            assert (map != null);
            logger.log(Level.FINEST, "{0}: exchanging labels with {1}",
                       localNodeId, nodeId);
            Map<Object, Map<Integer, List<Long>>> labels = null;
            try {
                labels =
                    proxy.getRemoteLabels(new HashSet<Object>(map.keySet()));
            } catch (IOException e) {
                // JANE RETRY
                failed = true;
                logger.logThrow(Level.WARNING, e,
                        "{0}: could not contact node {1}", localNodeId, nodeId);
            }

            if (labels == null) {
                // This is unexpected; the other node should have returned
                // an empty collection.  Log it, but act as if it
                // was an empty collection.
                logger.log(Level.FINE, "unexpected null labels");
                continue;
            }

            // Process the returned labels
            // For each object returned...
            for (Map.Entry<Object, Map<Integer, List<Long>>> remoteEntry :
                 labels.entrySet())
            {
                Object remoteObject = remoteEntry.getKey();
                // ... look up the local node use of the object.
                ConcurrentMap<Identity, AtomicLong> objUse =
                        objectMap.get(remoteObject);
                if (objUse == null) {
                    // no local uses of this object
                    continue;
                }
                Map<Integer, List<Long>> remoteLabels = remoteEntry.getValue();
                // Compare each local use's weight with each remote use of
                // the weight, and fill in our remoteLabelMap.
                for (Map.Entry<Identity, AtomicLong> objUseId :
                    objUse.entrySet())
                {
                    Identity ident = objUseId.getKey();
                    long localCount = objUseId.getValue().get();
                    Map<Integer, Long> labelCount = remoteLabelMap.get(ident);
                    if (labelCount == null) {
                        // Effective Java item 69, faster to use get before
                        // putIfAbsent
                        Map<Integer, Long> newMap =
                                new ConcurrentHashMap<Integer, Long>();
                        labelCount = remoteLabelMap.putIfAbsent(ident, newMap);
                        if (labelCount == null) {
                            labelCount = newMap;
                        }
                    }
                    for (Map.Entry<Integer, List<Long>> rLabelCount :
                        remoteLabels.entrySet())
                    {
                        Integer rlabel = rLabelCount.getKey();
                        List<Long> rcounts = rLabelCount.getValue();
                        Long updateCount = labelCount.get(rlabel);
                        if (updateCount == null) {
                            updateCount = Long.valueOf(0);
                        }
                        for (Long rc : rcounts) {
                            updateCount += Math.min(localCount, rc.longValue());
                        }
                        labelCount.put(rlabel, updateCount);
                        logger.log(Level.FINEST,
                                "{0}: label {1}, updateCount {2}, " +
                                "localCount {3}, : ident {4}",
                                localNodeId, rlabel, updateCount,
                                localCount, ident);
                    }
                }
            }
        }

        return failed;
    }
    
    /**
     * Returns the client for the given nodeId, asking the server if necessary.
     * @param nodeId
     * @return
     */
    private LPAClient getProxy(long nodeId) {
        LPAClient proxy = nodeProxies.get(nodeId);
        if (proxy == null) {
            // Ask the server for it.
            try {
                proxy = server.getLPAClientProxy(nodeId);
            } catch (IOException e) {
                // JANE retries
            }
            if (proxy != null) {
                nodeProxies.put(nodeId, proxy);
            } else {
                removeNodeInternal(nodeId);
            }
        }
        return proxy;
    }

    /**
     * Find the communities, using a graph obtained from the graph builder
     * provided at construction time.  The communities are found using the
     * label propagation algorithm.
     * <p>
     * This algorithm will not modify the graph by adding or removing vertices
     * or edges, but it will modify the labels in the vertices.
     * <p>
     * This implementation is for graphs on a single node only, and is useful
     * for testing algorithm optimizations.  Finding affinity groups on a
     * single node is, in general, not useful (the affinity groups are used
     * for load balancing, and no load balancing is required on a single node).
     *
     * @return the affinity groups
     */
    public Collection<AffinityGroup> singleNodeFindCommunities() {
        long startTime = System.currentTimeMillis();

        // Step 1.  Initialize all nodes in the network.
        //          Their labels are their Identities.
        
        // JANE The WeightedGraphBuilder returns a live graphs, the other
        // variations are returning snapshots.  If we got to only the
        // weighted graph builder, can make the graph field final and
        // set it in the constructor.
        initializeLPARun();

        // Step 2.  Set t = 1;
        int t = 1;

        while (true) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "{0}: GRAPH at iteration {1} is {2}",
                                          localNodeId, t, graph);
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
                            abool.set(setMostFrequentLabel(vertex, true) ||
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
                    changed = setMostFrequentLabel(vertex, true) || changed;
                }
            }

            // Step 5. If every vertex has a label that the maximum number of
            //         their neighbors have, then stop.   Otherwise, set
            //         t++ and loop.
            // Note that Leung's paper suggests we don't need the extra stopping
            // condition if we include each vertex in the neighbor freq calc.
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
                               "{0}: Intermediate group {1} , members: {2}",
                               localNodeId, group, logSB.toString());
                }

            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "{0}: FINAL GRAPH IS {1}",
                                    localNodeId, graph);
        }
        groups = gatherGroups(vertices, true);

        if (gatherStats) {
            // Record our statistics for this run, used for testing.
            time = System.currentTimeMillis() - startTime;
            iterations = t;
            // Note that the graph might be changing while we ran
            // the algorithm.
            modularity = AffinityGroupGoodness.calcModularity(graph, groups);

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

    // Private methods used by both single-node test and distributed
    // algorithms.
    /**
     * Initialize ourselves for a run of the algorithm.
     */
    private void initializeLPARun() {
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
        logger.log(Level.FINEST,
                   "{0}: finished initializing LPA run", localNodeId);
    }

    /**
     * Initialize our vertex conflicts.  This needs to happen before
     * we send our vertex conflict information to other nodes in
     * response to an prepareAlgorithm call from the server, and before 
     * any crossNodeEdges calls.
     */
    private void initializeNodeConflictMap() {
        // Get conflict information from the graph builder.
        nodeConflictMap.putAll(builder.getConflictMap());
        logger.log(Level.FINEST,
                "{0}: initialized node conflict map", localNodeId);
        printNodeConflictMap();
    }

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
    private boolean setMostFrequentLabel(LabelVertex vertex, boolean self) {
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
     * with the highest count amongst {@code vertex}'s neighbors
     *
     * @param vertex the vertex whose neighbors labels will be examined
     * @return a list of labels with the higest counts
     */
    private List<Integer> getNeighborCounts(LabelVertex vertex) {
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

        // Account for the remote neighbors:  look up this LabelVertex in
        // the remoteNeighborMap
        Map<Integer, Long> remoteMap =
                remoteLabelMap.get(vertex.getIdentity());
        if (remoteMap != null) {
            // The check above is just so I can continue to test in single 
            // node mode
            for (Map.Entry<Integer, Long> entry : remoteMap.entrySet()) {
                Integer label = entry.getKey();
                if (logger.isLoggable(Level.FINEST)) {
                    logSB.append("RLabel:" + label + 
                                 "(" + entry.getValue() + ") ");
                }
                Long value = labelMap.containsKey(label) ?
                                labelMap.get(label) : 0;
                value += entry.getValue();
                labelMap.put(label, value);
            }
        }


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

    // Utility methods
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
    public static Collection<AffinityGroup> gatherGroups(
            List<LabelVertex> vertices, boolean reinitialize)
    {
        assert (vertices != null);
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
            if (reinitialize) {
                vertex.initializeLabel();
            }
        }
        return groupMap.values();
    }

    // For single node performance testing.
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

    // For debugging.
    private void printNodeConflictMap() {
        if (!logger.isLoggable(Level.FINEST)) {
            return;
        }
        for (Map.Entry<Long, ConcurrentMap<Object, AtomicLong>> entry :
             nodeConflictMap.entrySet())
        {
            StringBuilder sb = new StringBuilder();
            sb.append(entry.getKey());
            sb.append(":  ");
            for (Map.Entry<Object, AtomicLong> subEntry :
                 entry.getValue().entrySet())
            {
                sb.append(subEntry.getKey() + "," + subEntry.getValue() + " ");
            }
            logger.log(Level.FINEST, "{0}: nodeConflictMap: {1}",
                    localNodeId, sb.toString());
        }
    }

    // For testing
    /**
     * Returns a copy of the node conflict map.
     * @return a copy of the node conflict map.
     */
    public ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
            getNodeConflictMap()
    {
        ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>> copy =
            new ConcurrentHashMap<Long, ConcurrentMap<Object, AtomicLong>>(
                                                            nodeConflictMap);
        return copy;
    }

    /**
     * Returns a copy of the remote label map.
     * @return a copy of the remote label map
     */
    public ConcurrentMap<Identity, Map<Integer, Long>> getRemoteLabelMap() {
        ConcurrentMap<Identity, Map<Integer, Long>> copy =
            new ConcurrentHashMap<Identity, Map<Integer, Long>>(remoteLabelMap);
        return copy;
    }
}
