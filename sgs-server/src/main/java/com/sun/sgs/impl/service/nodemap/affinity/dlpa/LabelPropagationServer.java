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

package com.sun.sgs.impl.service.nodemap.affinity.dlpa;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import
   com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderFailedException;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.BasicState;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.NamedThreadFactory;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * The server portion of the distributed label propagation algorithm.
 * <p>
 * The server is known to each node participating in the algorithm.  It is
 * responsible for preparing the nodes for a run of the algorithm, coordinating
 * the iterations of the algorithm, and collecting and merging results from
 * each node when finished.
 */
public class LabelPropagationServer extends BasicState
        implements LPAAffinityGroupFinder, LPAServer
{
    /** Our property base name. */
    private static final String PROP_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** Our class name. */
    private static final String CLASS_NAME =
            LabelPropagationServer.class.getName();

    /** Our logger. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PROP_NAME));

    /** The property name for the server port. */
    public static final String SERVER_PORT_PROPERTY =
            PROP_NAME + ".server.port";
    
    /** The default value of the server port. */
    public static final int DEFAULT_SERVER_PORT = 44537;

    /** The name we export ourselves under. */
    public static final String SERVER_EXPORT_NAME = "LabelPropagationServer";

    /** The time, in minutes, to wait for all nodes to
     * respond to asynchronous calls.
     */
    private static final int TIMEOUT = 1;  // minutes

    /** The maximum number of iterations we will run.  Interesting to set high
     * for testing, but 5 has been shown to be adequate in most papers.
     * For distributed case, seem to always converge within 10, and setting
     * to 5 cuts off some of the highest modularity solutions (running
     * distributed Zachary test network).
     */
    private static final int MAX_ITERATIONS = 10;

    /** Prefix for io task related properties. */
    public static final String IO_TASK_PROPERTY_PREFIX =
            "com.sun.sgs.impl.util.io.task";

    /**
     * An optional property that specifies the maximum number of retries for
     * IO tasks in services.
     */
    public static final String IO_TASK_RETRIES_PROPERTY =
            IO_TASK_PROPERTY_PREFIX + ".max.retries";

    /**
     * An optional property that specifies the wait time between successive
     * IO task retries.
     */
    public static final String IO_TASK_WAIT_TIME_PROPERTY =
            IO_TASK_PROPERTY_PREFIX + ".wait.time";

    /** The default number of IO task retries. **/
    static final int DEFAULT_MAX_IO_ATTEMPTS = 5;

    /** The default time interval to wait between IO task retries. **/
    static final int DEFAULT_RETRY_WAIT_TIME = 100;

    /**
     * Our local watchdog service, used in case of IO failures.
     * Can be null for testing.
     */
    private final WatchdogService wdog;

    /** The time (in milliseconds) to wait between retries for IO
     * operations. */
    private final int retryWaitTime;

    /** The maximum number of retry attempts for IO operations. */
    private final int maxIoAttempts;

    /** The exporter for this serve. */
    private final Exporter<LPAServer> exporter;
    
    /* A map from node id to client proxy objects. */
    private final Map<Long, LPAClient> clientProxyMap =
            new ConcurrentHashMap<Long, LPAClient>();

    /** A barrier that consists of the set of nodes we expect to hear back
     * from asynchronous calls.  Once this set is empty, we can move on to the
     * next step of the algorithm.  This data structure is required, rather
     * than a simple Barrier, because our calls must be idempotent.
     */
    private final Set<Long> nodeBarrier =
            Collections.synchronizedSet(new HashSet<Long>());

    /**
     * A latch to ensure our main thread waits for all nodes to complete
     * each step of the algorithm before proceeding.
     * This is replaced on each iteration.
     * No synchronization is required on this latch.
     */
    private volatile CountDownLatch latch;

    // Algorithm iteration information
    /**
     * The current iteration of the algorithm, used for sanity checking;
     * and set in a single thread.
     */
    private int currentIteration;
    /** True if we believe all nodes have converged. */
    private volatile boolean nodesConverged;

    /** Set to true if something has gone wrong and the results from
     * this algorithm run should be ignored.
     */
    private volatile boolean runFailed;

    /**
     * The exception to be associated with runFailed, including a detail
     * message and causing exception (if there is one).  During an algorithm
     * iteration, this should be set once.
     */
    private volatile AffinityGroupFinderFailedException runException;

    /** A thread pool.  Will create as many threads as needed, with a timeout
     * of 60 sec before unused threads are reaped.
     */
    private final ExecutorService executor = Executors.newCachedThreadPool(
            new NamedThreadFactory("LabelPropagationServer"));

    // TBD:  we need to have a state, and not allow a run when we're shutting
    //     down, or shutdown while we're running.  Will also need an
    //     enable/disable.
    /** A lock to ensure we block a run of the algorithm if a current run
     * is still going.  TBD:  what behavior do we want?  Throw an exception?
     * "merge" the two run attempts - e.g. second run just returns the result
     * of the ongoing first one?  Abort the first one?
     */
    private final Object runningLock = new Object();
    /** True if we're in the midst of an algorithm run.  Access while holding
     * the runningLock.
     */
    private boolean running = false;

    /**  The algorithm run number, used to ensure that LPAClients are reporting
     * results from the expected run.
     */
    private final AtomicLong runNumber = new AtomicLong();

    /** Our JMX info. */
    private final AffinityGroupFinderStats stats;
    
    /**
     * Constructs a new label propagation server. Only one should exist
     * within a Darkstar cluster.
     * @param col the profile collector
     * @param wdog the watchdog service, used for error reporting
     * @param properties the application properties
     * @throws IOException if an error occurs
     */
    public LabelPropagationServer(ProfileCollector col, WatchdogService wdog,
            Properties properties)
        throws IOException
    {
        this.wdog = wdog;
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        // Retry behavior
        retryWaitTime = wrappedProps.getIntProperty(
                IO_TASK_WAIT_TIME_PROPERTY, DEFAULT_RETRY_WAIT_TIME, 0,
                Integer.MAX_VALUE);
        maxIoAttempts = wrappedProps.getIntProperty(
                IO_TASK_RETRIES_PROPERTY, DEFAULT_MAX_IO_ATTEMPTS, 0,
                Integer.MAX_VALUE);

        // Register our node listener with the watchdog service.
        wdog.addNodeListener(new NodeFailListener());
        
        int requestedPort = wrappedProps.getIntProperty(
                SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
        // Export ourself.
        exporter = new Exporter<LPAServer>(LPAServer.class);
        exporter.export(this, SERVER_EXPORT_NAME, requestedPort);

        // Create our JMX MBean
        stats = new AffinityGroupFinderStats(this, col, MAX_ITERATIONS);
        try {
            col.registerMBean(stats, AffinityGroupFinderMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            // Continue on if we couldn't register this bean, although
            // it's probably a very bad sign
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
    }

    // ---- Implement LPAAffinityGroupFinder --- //

    /** {@inheritDoc} */
    public NavigableSet<RelocatingAffinityGroup> findAffinityGroups()
            throws AffinityGroupFinderFailedException
    {
        checkForDisabledOrShutdownState();
        synchronized (runningLock) {
            while (running) {
                try {
                    runningLock.wait();
                } catch (InterruptedException e) {
                    throw new AffinityGroupFinderFailedException(
                               "Interrupted while waiting for current run", e);
                }
            }
            running = true;
        }
        // This server controls the running of the distributed label
        // propagation algorithm, using the LPAServer and LPAClient
        // interfaces.  The protocol is:
        // Server calls each LPAClient.prepareAlgorithm().
        //     Nodes contact other nodes which their graphs might be
        //     connected to, using LPAClient.notifyCrossNodeEdges.
        //     Nodes can find the appropriate LPAClient by calling
        //     LPAServer.getLPAClientProxy.
        //     When finished exchanging information, each node calls
        //     LPAServer.readyToBegin().
        // Server begins iterations of the algorithm.  For each iteration,
        // it calls LPAClient.startIteration().
        //     Nodes compute one iteration of the label propagation algorithm.
        //     Remote information (cross node edges discovered above) can
        //     be found by calling LPAClient.getRemoteLabels on other nodes.
        //     When finished, each node calls LPAServer.finishedIteration,
        //     noting whether it believes the algorithm has converged.
        // When all nodes agree that the algorithm has converged, or many
        // iterations have been run, the server gathers all group information
        // from each node by calling LPAClient.getAffinityGroups().  The server
        // combines groups that might cross nodes, and creates new, final
        // affinity group information.
        long startTime = System.currentTimeMillis();
        stats.runsCountInc();

        // Don't pay any attention to changes while we're running, at least
        // to start with.  If a node fails, we'll stop and return no information
        // for now.  When a node fails, a lot of changes will occur in the
        // graphs as we move identities from the failed node to new nodes.
        //
        // We make this copy unmodifiable to catch any errors in our code
        // breaking this assumption.
        final Map<Long, LPAClient> clientProxyCopy =
                Collections.unmodifiableMap(
                    new HashMap<Long, LPAClient>(clientProxyMap));
        
        runFailed = false;
        runException = null;
        nodesConverged = false;

        // Tell each node to prepare for the algorithm to start
        prepareAlgorithm(clientProxyCopy);

        if (runFailed) {
            handleFailure("could not prepare");
            throw runException;
        }

        if (logger.isLoggable(Level.FINE)) {
            long time = System.currentTimeMillis() - startTime;
            logger.log(Level.FINE,
                       "Algorithm prepare took {0} milliseconds", time);
        }

        // Run the algorithm in multiple iterations, until it has runFailed
        // or converged
        runIterations(clientProxyCopy);

        // Now, gather up our results
        if (runFailed) {
            handleFailure("could not complete iterations");
            throw runException;
        }

        // If, after this point, we cannot contact a node, simply
        // return the information that we have.
        // Assuming a node has failed, we won't report the identities
        // on the failed node as being part of any group.
        NavigableSet<RelocatingAffinityGroup> retVal =
                gatherFinalGroups(clientProxyCopy);

        long runTime = System.currentTimeMillis() - startTime;
        stats.runtimeSample(runTime);
        stats.iterationsSample(currentIteration);
        stats.setNumGroups(retVal.size());
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Algorithm took {0} milliseconds and {1} " +
                    "iterations", runTime, currentIteration);
            StringBuilder sb = new StringBuilder();
            sb.append(" LPA found " +  retVal.size() + " groups ");

            for (AffinityGroup group : retVal) {
                sb.append(" id: " + group.getId() + ": members ");
                for (Identity id : group.getIdentities()) {
                    sb.append(id + " ");
                }
            }
            logger.log(Level.FINE, sb.toString());
        }

        synchronized (runningLock) {
            running = false;
            runningLock.notifyAll();
        }
        return retVal;
    }

    /**
     * Helper function while cleans up on failure and sets {@code runException}
     * if it has not yet been set.
     */
    private void handleFailure(String msg) {
        synchronized (runningLock) {
            running = false;
            runningLock.notifyAll();
        }
        stats.failedCountInc();
        stats.setNumGroups(0);
        if (runException == null) {
            runException = new AffinityGroupFinderFailedException(msg);
        }
    }

    /** {@inheritDoc} */
    public void disable() {
        if (setDisabledState()) {
            for (Map.Entry<Long, LPAClient> ce : clientProxyMap.entrySet()) {
                runIoTask(new DisableTask(ce.getValue()),
                    wdog, ce.getKey(), maxIoAttempts,
                    retryWaitTime, CLASS_NAME);
            }
        }
    }

    /**  Private task to disable a proxy. */
    private static class DisableTask implements IoRunnable {
        private final LPAClient proxy;
        DisableTask(LPAClient proxy) {
            this.proxy = proxy;
        }
        public void run() throws IOException {
            proxy.disable();
        }
    }

    /** {@inheritDoc} */
    public void enable() {
        if (setEnabledState()) {
            for (Map.Entry<Long, LPAClient> ce : clientProxyMap.entrySet()) {
                runIoTask(new EnableTask(ce.getValue()),
                    wdog, ce.getKey(), maxIoAttempts,
                    retryWaitTime, CLASS_NAME);
            }
        }
    }

    /**  Private task to enable a proxy. */
    private static class EnableTask implements IoRunnable {
        private final LPAClient proxy;
        EnableTask(LPAClient proxy) {
            this.proxy = proxy;
        }
        public void run() throws IOException {
            proxy.enable();
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        if (setShutdownState()) {
            for (Map.Entry<Long, LPAClient> ce : clientProxyMap.entrySet()) {
                try {
                    ce.getValue().shutdown();
                    clientProxyMap.remove(ce.getKey());
                } catch (IOException e) {
                    // It's OK if we cannot reach the client.  The entire system
                    // might be coming down.
                }
            }
            exporter.unexport();
            executor.shutdownNow();
        }
    }

    // --- Implement LPAServer --- //

    /** {@inheritDoc} */
    public void readyToBegin(long nodeId, boolean failed) throws IOException {
        if (failed) {
            String msg = "node " + nodeId + " reports failure preparing";
            if (runException == null) {
                runException = new AffinityGroupFinderFailedException(msg);
            }
            logger.log(Level.INFO, "node {0} reports failure", nodeId);
            runFailed = true;
        }
        maybeCountDown(nodeId);
    }

    /** {@inheritDoc} */
    public void finishedIteration(long nodeId, boolean converged, 
                                  boolean failed, int iteration)
            throws IOException
    {
        if (failed) {
            String msg = "node " + nodeId +
                         " reports failure in iteration " + iteration;
            if (runException == null) {
                runException = new AffinityGroupFinderFailedException(msg);
            }
            logger.log(Level.INFO, "node {0} reports failure", nodeId);
            runFailed = true;
        }
        if (iteration != currentIteration) {
            String msg = "node " + nodeId +
                         " reports unexpected iteration " + iteration;
            if (runException == null) {
                runException = new AffinityGroupFinderFailedException(msg);
            }
            logger.log(Level.INFO, "unexpected iteration: {0} on node {1}, " +
                    "expected {2}, marking run failed",
                    iteration, nodeId, currentIteration);
            runFailed = true;
        }
        nodesConverged = converged && nodesConverged;

        maybeCountDown(nodeId);
    }

    /** {@inheritDoc} */
    public LPAClient getLPAClientProxy(long nodeId) throws IOException {
        return clientProxyMap.get(nodeId);
    }


    /** {@inheritDoc} */
    public void register(long nodeId, LPAClient client) throws IOException {
        clientProxyMap.put(nodeId, client);
    }

    /**
     * The listener registered with the watchdog service.  These methods
     * will be notified if a node starts or stops.
     */
    private class NodeFailListener implements NodeListener {
        NodeFailListener() {
            // nothing special
        }

        /** {@inheritDoc} */
        public void nodeHealthUpdate(Node node) {
            switch (node.getHealth()) {
                case RED :
                    removeNode(node.getId());
                    break;
                default :
                    // do nothing
                    break;
            }
        }
    }

    /**
     * Removes cached information about a failed node.
     * @param nodeId the Id of the failed node
     */
    private void removeNode(long nodeId) {
        clientProxyMap.remove(nodeId);
    }

    /**
     * Tells each registred LPAClient to prepare for a run of the algorithm.
     * 
     * @param clientProxies a map of node ids to LPAClient proxies
     */
    private void prepareAlgorithm(Map<Long, LPAClient> clientProxies) {
        // Tell each node to prepare for an algorithm run.
        nodeBarrier.clear();
        nodeBarrier.addAll(clientProxies.keySet());
        latch = new CountDownLatch(clientProxies.keySet().size());

        final long runNum = runNumber.incrementAndGet();
        for (final Map.Entry<Long, LPAClient> ce : clientProxies.entrySet()) {
            long nodeId = ce.getKey();
            try {
                boolean ok = runIoTask(new IoRunnable() {
                    public void run() throws IOException {
                        ce.getValue().prepareAlgorithm(runNum);
                    } }, wdog, nodeId, maxIoAttempts, retryWaitTime,
                         CLASS_NAME);
                if (!ok) {
                    String msg = "node " + nodeId +
                                 " could not be contacted to prepare " + runNum;
                    if (runException == null) {
                        runException =
                                new AffinityGroupFinderFailedException(msg);
                    }
                    runFailed = true;
                    maybeCountDown(nodeId);
                    // If we cannot reach the proxy after retries, we need
                    // to remove it from the
                    // clientProxyMap.
                    removeNode(nodeId);
                }
            } catch (Exception e) {
                String msg = "node " + nodeId +
                             " exception while preparing " + runNum;
                if (runException == null) {
                    runException = 
                            new AffinityGroupFinderFailedException(msg, e);
                }
                logger.logThrow(Level.INFO, e,
                    "exception from node {0} while preparing",
                    nodeId);
                runFailed = true;
                maybeCountDown(nodeId);
            }
        }

        // Wait for the initialization to complete on all nodes
        waitOnLatch();
    }

    /**
     * Run the algorithm iterations until all LPAClients have converged.
     * @param clientProxies a map of node ids to LPAClient proxies
     */
    private void runIterations(Map<Long, LPAClient> clientProxies) {
        final int cleanSize = clientProxies.keySet().size();
        currentIteration = 0;
        while (!runFailed && !nodesConverged) {
            // Assume we'll converge unless told otherwise; all nodes must
            // say we've converged for nodesConverged to remain true in
            // this iteration
            nodesConverged = true;
            assert (nodeBarrier.isEmpty());
            nodeBarrier.addAll(clientProxies.keySet());
            latch = new CountDownLatch(cleanSize);
            for (final Map.Entry<Long, LPAClient> ce : clientProxies.entrySet())
            {
                long nodeId = ce.getKey();
                try {
                    boolean ok = runIoTask(new IoRunnable() {
                    public void run() throws IOException {
                        ce.getValue().startIteration(currentIteration);
                    } }, wdog, nodeId, maxIoAttempts, retryWaitTime,
                         CLASS_NAME);
                    if (!ok) {
                        String msg = "node " + nodeId +
                                     " could not be contacted for iteration " +
                                     currentIteration;
                        if (runException == null) {
                            runException =
                                    new AffinityGroupFinderFailedException(msg);
                        }
                        runFailed = true;
                        maybeCountDown(nodeId);
                        // If we cannot reach the proxy after retries, we need
                        // to remove it from the
                        // clientProxyMap.
                        removeNode(nodeId);
                    }
                } catch (Exception e) {
                    String msg = "node " + nodeId +
                                 " exception for iteration " +
                                 currentIteration;
                    if (runException == null) {
                        runException =
                                new AffinityGroupFinderFailedException(msg, e);
                    }
                    logger.logThrow(Level.INFO, e,
                        "exception from node {0} while running " +
                        "iteration {1}",
                        nodeId, currentIteration);
                    runFailed = true;
                    maybeCountDown(nodeId);
                }
            }
            // Wait for all nodes to complete this iteration
            waitOnLatch();
            // Papers show most work is done after 5 iterations
            if (++currentIteration >= MAX_ITERATIONS) {
                stats.stoppedCountInc();
                logger.log(Level.FINE, "exceeded {0} iterations, stopping",
                        MAX_ITERATIONS);
                break;
            }
        }
    }

    /**
     * Ask each of the LPAClients for the final affinity groups they found,
     * also asking them to prepare for the next algorithm run.  The affinity
     * groups are then merged, so the final groups can span nodes.
     *
     * @param clientProxies a map of node ids to LPAClient proxies
     * @return the merged affinity groups found on each LPAClient
     */
    private NavigableSet<RelocatingAffinityGroup> gatherFinalGroups(
                    Map<Long, LPAClient> clientProxies)
    {
        // If, after this point, we cannot contact a node, simply
        // return the information that we have.
        // Assuming a node has failed, we won't report the identities
        // on the failed node as being part of any group.
        final Map<Long, Set<AffinityGroup>> returnedGroups =
            new ConcurrentHashMap<Long, Set<AffinityGroup>>();
        nodeBarrier.clear();
        nodeBarrier.addAll(clientProxies.keySet());
        latch = new CountDownLatch(clientProxies.keySet().size());
        final long runNum = runNumber.get();
        for (final Map.Entry<Long, LPAClient> ce : clientProxies.entrySet()) {
            final Long nodeId = ce.getKey();
            final LPAClient proxy = ce.getValue();
            // TODO:  use executor to make parallel requests
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        boolean ok = runIoTask(new IoRunnable() {
                            public void run() throws IOException {
                                returnedGroups.put(nodeId,
                                      proxy.getAffinityGroups(runNum, true));
                            } }, wdog, nodeId, maxIoAttempts, retryWaitTime,
                                 CLASS_NAME);
                        maybeCountDown(nodeId);
                        if (!ok) {
                            removeNode(ce.getKey());
                        }
                    } catch (Exception e) {
                        logger.logThrow(Level.INFO, e,
                            "exception from node {0} while returning groups",
                            ce.getKey());
                        maybeCountDown(nodeId);
                    }
                }
            });
        }

        // Wait for the calls to complete on all nodes
        waitOnLatch();

        // Map of group id -> identity, node
        Map<Long, Map<Identity, Long>> groupMap =
                new HashMap<Long, Map<Identity, Long>>();
        // Ensure that each identity is only assigned to a single group
        Set<Identity> idSet = new HashSet<Identity>();
        for (Map.Entry<Long, Set<AffinityGroup>> e :
            returnedGroups.entrySet())
        {
            Long nodeId = e.getKey();
            for (AffinityGroup ag : e.getValue()) {
                long id = ag.getId();
                Map<Identity, Long> idNodeMap = groupMap.get(id);
                if (idNodeMap == null) {
                    idNodeMap = new HashMap<Identity, Long>();
                    groupMap.put(id, idNodeMap);
                }
                for (Identity gid : ag.getIdentities()) {
                    if (idSet.add(gid)) {
                        // Only add if this is the first time we've seen
                        // the identity.  The group selected is the first
                        // one seen, as added to the returnedGroups from
                        // the proxy calls.
                        idNodeMap.put(gid, nodeId);
                    }
                }
            }
        }

        // Create our final return values
        NavigableSet<RelocatingAffinityGroup> retVal =
                new TreeSet<RelocatingAffinityGroup>();
        for (Map.Entry<Long, Map<Identity, Long>> e : groupMap.entrySet()) {
            retVal.add(new RelocatingAffinityGroup(e.getKey(), 
                                                   e.getValue(),
                                                   runNum));
        }

        return retVal;
    }

    /**
     * Wait on the global latch, noting if the wait was not successful.
     */
    private void waitOnLatch() {
        try {
            boolean ok = latch.await(TIMEOUT, TimeUnit.MINUTES);
            if (!ok) {
                // We timed out on the latch, invalidating this run.
                String msg = "Latch timed out";
                if (runException == null) {
                    runException = new AffinityGroupFinderFailedException(msg);
                }
                runFailed = true;
            }
        } catch (InterruptedException ex) {
            String msg = "Latch timed interrupted";
            if (runException == null) {
                runException = new AffinityGroupFinderFailedException(msg, ex);
            }
            runFailed = true;
        }
    }

    /**
     * Calls countDown on {@code latch} if the given node ID is in
     * the {@code nodeBarrier}.
     * 
     * @param nodeId the ID of the node we're accounting for
     */
    private void maybeCountDown(long nodeId) {
        if (nodeBarrier.remove(nodeId)) {
            latch.countDown();
        }
    }
    /**
     * Executes the specified {@code ioTask} by invoking its {@link
     * IoRunnable#run run} method. If the specified task throws an
     * {@code IOException}, this method will retry the task for a fixed
     * number of times. The number of retries and the wait time between
     * retries are configurable properties.
     * <p>
     * This is much the same as the like method in AbstractService, except
     * we don't bother to check for a transactional context (we won't be in
     * one).
     *
     * @param ioTask a task with IO-related operations
     * @param wdog the watchdog service for the local node, in case of failure
     * @param nodeId the node that should be shut down in case of failure
     * @param maxTries the number of times to attempt the retry
     * @param waitTime the amount of time to wait before retry
     * @param name name of caller, in case of failure
     *
     * @return {@code true} if the ioTask ran successfully
     */
    static boolean runIoTask(IoRunnable ioTask, WatchdogService wdog, 
                            long nodeId, int maxTries, int waitTime,
                            String name)
    {
        int maxAttempts = maxTries;
        while (maxAttempts > 0) {
            try {
                ioTask.run();
                return true;
            } catch (IOException e) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.logThrow(Level.FINEST, e,
                            "IoRunnable {0} throws", ioTask);
                }
                try {
                    // TBD: what back-off policy do we want here?
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                }
            }
        }
        logger.log(Level.WARNING,
                "A communication error occured while running an" +
                "IO task. Could not reach node {0}.", nodeId);
        wdog.reportFailure(nodeId, name);
        return false;
    }
}
