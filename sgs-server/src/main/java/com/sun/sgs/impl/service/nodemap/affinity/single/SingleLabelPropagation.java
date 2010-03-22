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

package com.sun.sgs.impl.service.nodemap.affinity.single;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AbstractLPA;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import
   com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderFailedException;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupGoodness;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.ProfileCollector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.management.JMException;

/**
 * A single-node implementation of the algorithm presented in
 * "Near linear time algorithm to detect community structures in large-scale
 * networks" Raghavan, Albert and Kumara 2007.
 */
public class SingleLabelPropagation extends AbstractLPA 
        implements LPAAffinityGroupFinder
{
    /** Our graph builder. */
    private final AffinityGraphBuilder builder;

    /** Our JMX info. */
    private final AffinityGroupFinderStats stats;

    /** Our generation number. */
    private final AtomicLong generation = new AtomicLong();

    /** The maximum number of iterations we will run.  Interesting to set high
     * for testing, but 5 has been shown to be adequate in most papers.
     * For distributed case, seem to always converge within 10, and setting
     * to 5 cuts off some of the highest modularity solutions (running
     * distributed Zachary test network).
     */
    private static final int MAX_ITERATIONS = 10;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param col the profile collector
     * @param properties the properties for configuring this service
     *
     * @throws IllegalArgumentException if {@code numThreads} is
     *       less than {@code 1}
     * @throws Exception if any other error occurs
     */
    public SingleLabelPropagation(AffinityGraphBuilder builder,
                                  ProfileCollector col,
                                  Properties properties)
        throws Exception
    {
        this(builder, col, properties, null);
    }

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param col the profile collector
     * @param properties the properties for configuring this service
     * @param stats pre-constructed JMX Mbean or {@code null} if one should be
     *              constructed
     * @throws IllegalArgumentException if {@code numThreads} is
     *       less than {@code 1}
     * @throws Exception if any other error occurs
     */
    public SingleLabelPropagation(AffinityGraphBuilder builder,
                                  ProfileCollector col,
                                  Properties properties,
                                  AffinityGroupFinderStats stats)
        throws Exception
    {
        super(1, properties);
        if (builder == null) {
	    throw new NullPointerException("null builder");
	}
        this.builder = builder;
        if (stats == null) {
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
        this.stats = stats;
    }

    /** {@inheritDoc} */
    protected void doOtherInitialization() {
        // do nothing
    }

    /** {@inheritDoc} */
    protected long doOtherNeighbors(LabelVertex vertex,
                                    Map<Integer, Long> labelMap,
                                    StringBuilder logSB)
    {
        // do nothing, no changes
        return -1L;
    }

    /** {@inheritDoc} */
    public void disable() {
        setDisabledState();
    }

    /** {@inheritDoc} */
    public void enable() {
        setEnabledState();
    }

    /** {@inheritDoc} */
    public void shutdown() {
        if (setShutdownState()) {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
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
    public NavigableSet<RelocatingAffinityGroup> findAffinityGroups()
            throws AffinityGroupFinderFailedException
    {
        checkForDisabledOrShutdownState();
        long startTime = System.currentTimeMillis();
        stats.runsCountInc();
        long gen = generation.incrementAndGet();

        // Step 1.  Initialize all nodes in the network.
        //          Their labels are their Identities.

        initializeLPARun(builder);

        // Step 2.  Set t = 1;
        int t = 1;

        while (true) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "{0}: GRAPH at iteration {1} is {2}",
                                          localNodeId, t, graph);
            }
            // Step 3.  Arrange the nodes in a random order and set it to X.
            // Choose a different ordering for each iteration
            if (t > 1) {
                Collections.shuffle(vertices);
            }

            // Step 4.  For each vertices in X chosen in that specific order,
            //          let the label of vertices be the label of the highest
            //          frequency of its neighbors.
            boolean changed = false;    

            if (numThreads > 1) {
                final AtomicBoolean abool = new AtomicBoolean(false);
                List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
                for (final LabelVertex vertex : vertices) {
                    tasks.add(new Callable<Void>() {
                        public Void call() {
                            if (setMostFrequentLabel(vertex, true)) {
                                abool.set(true);
                            }
                            return null;
                        }
                    });
                }

                // Invoke all the tasks, waiting for them to be done.
                // We don't look at the returned futures.
                try {
                    executor.invokeAll(tasks);
                    changed = abool.get();
                } catch (InterruptedException ie) {
                    changed = true;
                    logger.logThrow(Level.INFO, ie,
                                    " during iteration " + t);
                }

            } else {
                for (LabelVertex vertex : vertices) {
                    if (setMostFrequentLabel(vertex, true)) {
                        changed = true;
                    }
                }
            }

            // Step 5. If every vertex has a label that the maximum number of
            //         their neighbors have, then stop.   Otherwise, set
            //         t++ and loop.
            if (!changed) {
                break;
            }

            if (logger.isLoggable(Level.FINEST)) {
                // Log the affinity groups so far:
                Set<AffinityGroup> intermediateGroups =
                        gatherGroups(vertices, false, gen);
                for (AffinityGroup group : intermediateGroups) {
                    StringBuilder logSB = new StringBuilder();
                    for (Identity id : group.getIdentities()) {
                        logSB.append(id + " ");
                    }
                    logger.log(Level.FINEST,
                               "{0}: Intermediate group {1} , members: {2}",
                               localNodeId, group, logSB.toString());
                }

            }

            // Papers show most work is done after 5 iterations
            if (++t >= MAX_ITERATIONS) {
                stats.stoppedCountInc();
                logger.log(Level.FINE, "exceeded {0} iterations, stopping",
                        MAX_ITERATIONS);
                break;
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "{0}: FINAL GRAPH IS {1}",
                                    localNodeId, graph);
        }
        // The groups collected in the last run
        Set<AffinityGroup> groups = gatherGroups(vertices, true, gen);
        long runTime = System.currentTimeMillis() - startTime;
        stats.runtimeSample(runTime);
        stats.iterationsSample(t);
        stats.setNumGroups(groups.size());
        
        if (logger.isLoggable(Level.FINE)) {
            double modularity =
                    AffinityGroupGoodness.calcModularity(graph, groups);
            StringBuilder sb = new StringBuilder();
            sb.append(" LPA (" + numThreads + ") took " +
                      runTime + " milliseconds, " +
                      t + " iterations, and found " +
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
        
        // Need to translate the groups into relocating groups.
        // We do not know the group number, so just use -1.
        NavigableSet<RelocatingAffinityGroup> retVal =
                new TreeSet<RelocatingAffinityGroup>();
        for (AffinityGroup ag : groups) {
            Map<Identity, Long> idMap = new HashMap<Identity, Long>();
            for (Identity id : ag.getIdentities()) {
                idMap.put(id, -1L);
            }
            retVal.add(new RelocatingAffinityGroup(ag.getId(), idMap, gen));
        }
        return retVal;
    }
}
