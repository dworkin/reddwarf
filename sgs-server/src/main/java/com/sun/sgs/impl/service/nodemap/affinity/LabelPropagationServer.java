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
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.Exporter;
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
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final int TIMEOUT = 10;  // minutes

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
    private boolean failed;
    
    // A thread pool.  Will create as many threads as needed (I was having
    // starvation problems using a fixed thread pool - JANE?), with a timeout
    // of 60 sec before unused threads are reaped.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Constructs a new label propagation server. Only one should exist
     * within a Darkstar cluster.
     * @param properties the application properties
     * @throws IOException if an error occurs
     */
    public LabelPropagationServer(Properties properties) throws IOException {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        int requestedPort = wrappedProps.getIntProperty(
                SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
        // Export ourself.
        exporter = new Exporter<LPAServer>(LPAServer.class);
        exporter.export(this, SERVER_EXPORT_NAME, requestedPort);
    }

    // ---- Implement AffinityGroupFinder --- //

    /** {@inheritDoc} */
    public Collection<AffinityGroup> findAffinityGroups() {
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
        // from each node by calling LPAClient.affinityGroups().  The server
        // combines groups that might cross nodes, and creates new, final
        // affinity group information.
        long startTime = System.currentTimeMillis();

        // Don't pay any attention to changes while we're running, at least
        // to start with.  If a node fails, we'll stop and return no information
        // for now.  JANE does that make sense?  If a node fails, a lot of
        // churn will be created.
        final Map<Long, LPAClient> clientProxyCopy =
                new HashMap<Long, LPAClient>(clientProxyMap);
        
        failed = false;
        nodesConverged = false;

        // Tell each node to prepare for the algorithm to start
        prepareAlgorithm(clientProxyCopy);

        if (logger.isLoggable(Level.FINE)) {
            long time = System.currentTimeMillis() - startTime;
            logger.log(Level.FINE,
                       "Algorithm prepare took {0} milliseconds", time);
        }

        // Run the algorithm in multiple iterations, until it has failed
        // or converged
        runIterations(clientProxyCopy);

        // Now, gather up our results
        if (failed) {
            return new HashSet<AffinityGroup>();
        }

        // If, after this point, we cannot contact a node, simply
        // return the information that we have.
        // Assuming a node has failed, we won't report the identities
        // on the failed node as being part of any group.  
        final Set<AffinityGroup> returnedGroups =
                Collections.synchronizedSet(new HashSet<AffinityGroup>());
        latch = new CountDownLatch(clientProxyCopy.keySet().size());
        for (final LPAClient proxy : clientProxyCopy.values()) {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        returnedGroups.addAll(proxy.affinityGroups(true));
                    } catch (IOException ioe) {
                        failed = true;
                        ioe.printStackTrace();
                        // NEED retry here.
                    } catch (Exception e) {
                        failed = true;
                    } finally {
                        // this will need to change when we have retries
                        latch.countDown();
                    }
                }
            });
        }

        // Wait for the calls to complete on all nodes
        waitOnLatch();

        Map<Long, AffinityGroupImpl> groupMap =
                new HashMap<Long, AffinityGroupImpl>();
        for (AffinityGroup ag : returnedGroups) {
            long id = ag.getId();
            AffinityGroupImpl group = groupMap.get(id);
            if (group == null) {
                group = new AffinityGroupImpl(id);
                groupMap.put(id, group);
            }
            for (Identity gid : ag.getIdentities()) {
                group.addIdentity(gid);
            }
        }

        // convert our types
        Collection<AffinityGroup> retVal = new HashSet<AffinityGroup>();
        for (AffinityGroupImpl agi : groupMap.values()) {
            retVal.add(agi);
        }

        if (logger.isLoggable(Level.FINE)) {
            long time = System.currentTimeMillis() - startTime;
            logger.log(Level.FINE, "Algorithm took {0} milliseconds and {1} " +
                    "iterations", time, currentIteration);
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
        executor.shutdownNow();
        exporter.unexport();
    }

    // --- Implement LPAServer --- //

    /** {@inheritDoc} */
    public void readyToBegin(long nodeId, boolean failed) throws IOException {
        if (nodeBarrier.remove(nodeId)) {
            latch.countDown();
        }
        this.failed = this.failed || failed;
    }

    /** {@inheritDoc} */
    public void finishedIteration(long nodeId, boolean converged, 
                                  boolean failed, int iteration)
            throws IOException
    {
        this.failed = this.failed || failed;
        if (iteration != currentIteration) {
            // THINGS ARE VERY CONFUSED - need to log this
            failed = true;
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

        for (final Map.Entry<Long, LPAClient> ce : clientProxies.entrySet()) {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        ce.getValue().prepareAlgorithm();
                    } catch (IOException ioe) {
                        failed = true;
                        // NEED retry logic here.  If we cannot reach
                        // the proxy, we need to remove it from the
                        // clientProxyMap.
                        removeNode(ce.getKey());
                    }
                }
            });
        }

        // Wait for the initialization to complete on all nodes
        waitOnLatch();
    }

    private void runIterations(Map<Long, LPAClient> clientProxies) {
        final Set<Long> clean =
                Collections.unmodifiableSet(clientProxies.keySet());
        final int cleanSize = clean.size();
        currentIteration = 1;
        while (!failed && !nodesConverged) {
            // Assume we'll converge unless told otherwise; all nodes must
            // say we've converged for nodesConverged to remain true in
            // this iteration
            nodesConverged = true;
            assert (nodeBarrier.isEmpty());
            nodeBarrier.addAll(clean);
            latch = new CountDownLatch(cleanSize);
            for (final Map.Entry<Long, LPAClient> ce :
                       clientProxies.entrySet())
            {
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            ce.getValue().startIteration(currentIteration);
                        } catch (IOException ioe) {
                            failed = true;
                            // NEED retry logic here.  If we cannot reach
                            // the proxy, we need to remove it from the
                            // clientProxyMap.
                            removeNode(ce.getKey());
                        }
                    }
                });
            }
            // Wait for all nodes to complete this iteration
            waitOnLatch();
            // Papers show most work is done after 5 iterations
            if (++currentIteration > MAX_ITERATIONS) {
                // JANE - warning should be Level.FINE
                logger.log(Level.WARNING, "exceeded {0} iterations, stopping",
                        MAX_ITERATIONS);
                break;
            }
        }
    }
    
    private void waitOnLatch() {
        try {
            latch.await(TIMEOUT, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            failed = true;
        }
    }
}
