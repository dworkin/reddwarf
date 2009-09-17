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

package com.sun.sgs.impl.service.nodemap.affinity.dlpa;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.ProfileCollector;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
public class LabelPropagationServer implements AffinityGroupFinder, LPAServer {
    private static final String PKG_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    // Our logger
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The property name for the server port. */
    public static final String SERVER_PORT_PROPERTY = PKG_NAME + ".server.port";
    
    /** The default value of the server port. */
    public static final int DEFAULT_SERVER_PORT = 44537;

    /** The name we export ourselves under. */
    public static final String SERVER_EXPORT_NAME = "LabelPropagationServer";

    // The time, in minutes, to wait for all nodes to respond asynchronously
    private static final int TIMEOUT = 1;  // minutes

    // The maximum number of iterations we will run.  Interesting to set high
    // for testing, but 5 has been shown to be adequate in most papers.
    // For distributed case, seem to always converge within 10, and setting
    // to 5 cuts off some of the highest modularity solutions (running
    // distributed Zachary test network).
    private static final int MAX_ITERATIONS = 10;

    // The exporter for this server
    private final Exporter<LPAServer> exporter;
    
    // A map from node id to client proxy objects.
    private final Map<Long, LPAClient> clientProxyMap =
            new ConcurrentHashMap<Long, LPAClient>();

    // A barrier that consists of the set of nodes we expect to hear back
    // from asynchronous calls.  Once this set is empty, we can move on to the
    // next step of the algorithm.  This is required, rather than a simple
    // Barrier, because our calls must be idempotent.
    private Set<Long> nodeBarrier = new HashSet<Long>();

    // A latch to ensure our main thread waits for all nodes to complete
    // each step of the algorithm before proceeding.
    // This is replaced on each iteration.
    private CountDownLatch latch;

    // Algorithm iteration information
    private int currentIteration;
    private boolean nodesConverged;

    // Set to true if something has gone wrong and the results from
    // this algorithm run should be ignored
    private boolean runFailed;

    // A thread pool.  Will create as many threads as needed, with a timeout
    // of 60 sec before unused threads are reaped.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // A lock to ensure we block a run of the algorithm if a current run
    // is still going.  TBD:  what behavior do we want?  Throw an exception?
    // "merge" the two run attempts - e.g. second run just returns the result
    // of the ongoing first one?  Abort the first one?
    private final Object runningLock = new Object();
    private boolean running = false;

    // The iteration number, used to ensure that LPAClients are reporting
    // results from the expected iteration.
    private final AtomicLong runNumber = new AtomicLong();

    // Our JMX info
    private final LPFinderStats stats;
    
    /**
     * Constructs a new label propagation server. Only one should exist
     * within a Darkstar cluster.
     * @param col the profile collector
     * @param properties the application properties
     * @throws IOException if an error occurs
     */
    public LabelPropagationServer(ProfileCollector col, Properties properties)
            throws IOException
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        int requestedPort = wrappedProps.getIntProperty(
                SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
        // Export ourself.
        exporter = new Exporter<LPAServer>(LPAServer.class);
        exporter.export(this, SERVER_EXPORT_NAME, requestedPort);

        // Create our JMX MBean
        stats = new LPFinderStats(col, MAX_ITERATIONS);
        try {
            col.registerMBean(stats, AffinityGroupFinderMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            // Continue on if we couldn't register this bean, although
            // it's probably a very bad sign
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
    }

    // ---- Implement AffinityGroupFinder --- //

    /** {@inheritDoc} */
    public Collection<AffinityGroup> findAffinityGroups() {
        // Our return value, initally empty
        Collection<AffinityGroup> retVal = new HashSet<AffinityGroup>();
        synchronized (runningLock) {
            while (running) {
                try {
                    runningLock.wait();
                } catch (InterruptedException e) {
                    return retVal;
                }
            }
            running = true;
        }
        // This server controls the running of the distributed label
        // propagation algorithm, using the LPAServer and LPAClient
        // interfaces.  The protocol is:
        // Server calls each LPAClient.prepareAlgorithm().
        //     Nodes contact other nodes which their graphs might be
        //     connected to, using LPAClient.crossNodeEdges.
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
        final Map<Long, LPAClient> clientProxyCopy =
                new HashMap<Long, LPAClient>(clientProxyMap);
        
        runFailed = false;
        nodesConverged = false;

        // Tell each node to prepare for the algorithm to start
        prepareAlgorithm(clientProxyCopy);

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
            synchronized (runningLock) {
                running = false;
                runningLock.notifyAll();
            }
            stats.failedCountInc();
            stats.setNumGroups(0);
            return retVal;
        }

        // If, after this point, we cannot contact a node, simply
        // return the information that we have.
        // Assuming a node has failed, we won't report the identities
        // on the failed node as being part of any group.
        retVal = gatherFinalGroups(clientProxyCopy);

        long runTime = System.currentTimeMillis() - startTime;
        stats.runtimeSample(runTime);
        stats.iterationsSample(currentIteration);
        stats.setNumGroups(retVal.size());
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Algorithm took {0} milliseconds and {1} " +
                    "iterations", runTime, currentIteration);
            StringBuffer sb = new StringBuffer();
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

    /** {@inheritDoc} */
    public void removeNode(long nodeId) {
        synchronized (clientProxyMap) {
            clientProxyMap.remove(nodeId);
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        for (LPAClient client : clientProxyMap.values()) {
            try {
                client.shutdown();
            } catch (IOException e) {
                // JANE retry?  But it's OK if we cannot reach the client,
                // as the entire system might be coming down.
            }
        }
        exporter.unexport();
        executor.shutdownNow();
    }

    // --- Implement LPAServer --- //

    /** {@inheritDoc} */
    public void readyToBegin(long nodeId, boolean failed) throws IOException {
        if (failed) {
            logger.log(Level.INFO, "node {0} reports failure", nodeId);
        }
        runFailed = runFailed || failed;
        if (nodeBarrier.remove(nodeId)) {
            latch.countDown();
        }
    }

    /** {@inheritDoc} */
    public void finishedIteration(long nodeId, boolean converged, 
                                  boolean failed, int iteration)
            throws IOException
    {
        if (failed) {
            logger.log(Level.INFO, "node {0} reports failure", nodeId);
        }
        runFailed = runFailed || failed;
        if (iteration != currentIteration) {
            logger.log(Level.INFO, "unexpected iteration: {0} on node {1}, " +
                    "expected {2}, marking run failed",
                    iteration, nodeId, currentIteration);
            runFailed = true;
        }
        nodesConverged = converged && nodesConverged;

        if (nodeBarrier.remove(nodeId)) {
            latch.countDown();
        }
    }

    /** {@inheritDoc} */
    public LPAClient getLPAClientProxy(long nodeId) throws IOException {
        return clientProxyMap.get(nodeId);
    }


    /** {@inheritDoc} */
    public void register(long nodeId, LPAClient client) throws IOException {
        synchronized (clientProxyMap) {
            clientProxyMap.put(nodeId, client);
        }
    }

    /**
     * Tells each registred LPAClient to prepare for a run of the algorithm.
     * 
     * @param clientProxies a map of node ids to LPAClient proxies
     */
    private void prepareAlgorithm(Map<Long, LPAClient> clientProxies) {
        // Tell each node to prepare for an algorithm run.
        final Set<Long> clean =
                Collections.unmodifiableSet(clientProxies.keySet());
        nodeBarrier = Collections.synchronizedSet(new HashSet<Long>(clean));
        latch = new CountDownLatch(clean.size());

        final long runNum = runNumber.incrementAndGet();
        for (Map.Entry<Long, LPAClient> ce : clientProxies.entrySet()) {
            try {
                ce.getValue().prepareAlgorithm(runNum);
            } catch (IOException ioe) {
                runFailed = true;
                ioe.printStackTrace();
                // JANE NEED retry here.  If the retries fail,
                // be sure to count down the latch
                latch.countDown();
                // If we cannot reach the proxy after retries, we need
                // to remove it from the
                // clientProxyMap.
                removeNode(ce.getKey());
            } catch (Exception e) {
                logger.logThrow(Level.INFO, e,
                    "exception from node {0} while preparing",
                    ce.getKey());
                runFailed = true;
                latch.countDown();
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
        final Set<Long> clean =
                Collections.unmodifiableSet(clientProxies.keySet());
        final int cleanSize = clean.size();
        currentIteration = 0;
        while (!runFailed && !nodesConverged) {
            // Assume we'll converge unless told otherwise; all nodes must
            // say we've converged for nodesConverged to remain true in
            // this iteration
            nodesConverged = true;
            assert (nodeBarrier.isEmpty());
            nodeBarrier.addAll(clean);
            latch = new CountDownLatch(cleanSize);
            for (Map.Entry<Long, LPAClient> ce : clientProxies.entrySet()) {
                try {
                    ce.getValue().startIteration(currentIteration);
                } catch (IOException ioe) {
                    runFailed = true;
                    ioe.printStackTrace();
                    // JANE NEED retry here.  If the retries fail,
                    // be sure to count down the latch
                    latch.countDown();
                    // If we cannot reach the proxy after retries,
                    // we need to remove it from the clientProxyMap.
                    removeNode(ce.getKey());
                } catch (Exception e) {
                    logger.logThrow(Level.INFO, e,
                        "exception from node {0} while running " +
                        "iteration {1}",
                        ce.getKey(), currentIteration);
                    runFailed = true;
                    latch.countDown();
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
    private Collection<AffinityGroup> gatherFinalGroups(
            Map<Long, LPAClient> clientProxies)
    {
        // If, after this point, we cannot contact a node, simply
        // return the information that we have.
        // Assuming a node has failed, we won't report the identities
        // on the failed node as being part of any group.
        final Map<Long, Collection<AffinityGroup>> returnedGroups =
            new ConcurrentHashMap<Long, Collection<AffinityGroup>>();
        latch = new CountDownLatch(clientProxies.keySet().size());
        final long runNum = runNumber.get();
        for (final Map.Entry<Long, LPAClient> ce : clientProxies.entrySet()) {
            final Long nodeId = ce.getKey();
            final LPAClient proxy = ce.getValue();
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        returnedGroups.put(nodeId,
                                      proxy.getAffinityGroups(runNum, true));
                        latch.countDown();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                        // JANE NEED retry here.  If the retries fail,
                        // be sure to count down the latch
                        if (nodeBarrier.remove(nodeId)) {
                            latch.countDown();
                        }
                        removeNode(ce.getKey());
                    } catch (Exception e) {
                        logger.logThrow(Level.INFO, e,
                            "exception from node {0} while returning groups",
                            ce.getKey());
                        if (nodeBarrier.remove(nodeId)) {
                            latch.countDown();
                        }
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
        for (Map.Entry<Long, Collection<AffinityGroup>> e :
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
        Collection<AffinityGroup> retVal = new HashSet<AffinityGroup>();
        for (Map.Entry<Long, Map<Identity, Long>> e :
            groupMap.entrySet())
        {
            retVal.add(new RelocatingAffinityGroup(e.getKey(), e.getValue()));
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
                runFailed = true;
            }
        } catch (InterruptedException ex) {
            runFailed = true;
        }
    }
}
