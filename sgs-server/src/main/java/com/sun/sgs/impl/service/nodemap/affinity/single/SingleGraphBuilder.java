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
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import
   com.sun.sgs.impl.service.nodemap.affinity.graph.AbstractAffinityGraphBuilder;
import
    com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilderStats;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.management.AffinityGraphBuilderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.TransactionProxy;
import edu.uci.ics.jung.graph.UndirectedGraph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Graphs;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import javax.management.JMException;

/**
 * A minimal graph builder for single node testing.  This is mostly a copy
 * of the WeightedGraphBuilder, with the parts about node conflicts deleted.
 */
public class SingleGraphBuilder extends AbstractAffinityGraphBuilder
        implements AffinityGraphBuilder
{
    /** Map for tracking object-> map of identity-> number accesses
     * (thus we keep track of the number of accesses each identity has made
     * for an object, to aid maintaining weighted edges).
     * TBD: consider changing this to another data structure not using
     * inner maps.
     */
    private final ConcurrentMap<Object, Map<Identity, Long>> objectMap =
            new ConcurrentHashMap<Object, Map<Identity, Long>>();

    /** Our graph of object accesses. */
    private final UndirectedGraph<LabelVertex, WeightedEdge> affinityGraph =
            new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

    /**
     * A map of identity->graph vertex, allowing fast lookups of particular
     * vertices. Changes to this graph should occur atomically with vertex
     * changes to the affinity graph.
     */
    private final Map<Identity, LabelVertex> identMap =
            new HashMap<Identity, LabelVertex>();

    /** The TimerTask which prunes our data structures over time.  As the data
     * structures above are modified, the pruneTask notes the ways they have
     * changed.  Groups of changes are chunked into periods, each the length
     * of the time snapshot (configured at construction time). We
     * periodically remove the changes made in the earliest snapshot.
     */
    private final PruneTask pruneTask;

    /** Our JMX exposed information. */
    private volatile AffinityGraphBuilderStats stats;

    /** Our label propagation algorithm. */
    private final SingleLabelPropagation lpa;
    
    /**
     * Creates a weighted graph builder and its JMX MBean.
     * @param properties the properties for configuring this builder
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @throws Exception if an error occurs
     */
    public SingleGraphBuilder(Properties properties,
                              ComponentRegistry systemRegistry,
                              TransactionProxy txnProxy)
        throws Exception
    {
        this (properties, systemRegistry, txnProxy, true);
    }

    /**
     * Creates a weighted graph builder.  The JMX stats object may not be
     * created; this is useful for wrapper objects to break object dependencies.
     * If {@code needStats} is {@code false}, the stats object must be provided
     * with a call to {@code setStats} or a {@code NullPointerException} will
     * be thrown on the first call to {@code updateGraph}.
     * 
     * @param properties the properties for configuring this builder
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @param needStats {@code true} if stats should be constructed
     * @throws Exception if an error occurs
     */
    public SingleGraphBuilder(Properties properties,
                              ComponentRegistry systemRegistry,
                              TransactionProxy txnProxy,
                              boolean needStats)
        throws Exception
    {
        super(properties);

        ProfileCollector col =
                systemRegistry.getComponent(ProfileCollector.class);
        // Create the LPA algorithm
        lpa = new SingleLabelPropagation(this, col, properties);

        if (needStats) {
            // Create our JMX MBean
            stats = new AffinityGraphBuilderStats(col,
                        affinityGraph, periodCount, snapshot);
            try {
                col.registerMBean(stats,
                                  AffinityGraphBuilderMXBean.MXBEAN_NAME);
            } catch (JMException e) {
                // Continue on if we couldn't register this bean, although
                // it's probably a very bad sign
                logger.logThrow(Level.CONFIG, e, "Could not register MBean");
            }
        }
        
        pruneTask = new PruneTask(periodCount);
        Timer pruneTimer = new Timer("AffinityGraphPruner", true);
        pruneTimer.schedule(pruneTask, snapshot, snapshot);
    }

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        // TBD:  We don't currently use read/write access info.
        final Object[] ids = new Object[detail.getAccessedObjects().size()];
        int index = 0;
        for (AccessedObject access : detail.getAccessedObjects()) {
            ids[index++] = access.getObjectId();
        }
        updateGraph(owner, ids);
    }

    /**
     * Updates the graph with the given identity and object ids.
     * <p>
     * This method may be called by multiple threads and must protect itself
     * from changes to data structures made by the pruner.
     * @param owner the identity which accessed the objects
     * @param objIds the object ids of objects accessed by the identity
     */
    public void updateGraph(Identity owner, Object[] objIds) {
        checkForShutdownState();
        if (state == State.DISABLED) {
            return;
        }

        long startTime = System.currentTimeMillis();
        stats.updateCountInc();

        // For each object accessed in this task...
        for (Object objId : objIds) {
            // find the identities that have already used this object
            Map<Identity, Long> idMap = objectMap.get(objId);
            if (idMap == null) {
                // first time we've seen this object
                Map<Identity, Long> newMap = new HashMap<Identity, Long>();
                idMap = objectMap.putIfAbsent(objId, newMap);
                if (idMap == null) {
                    idMap = newMap;
                }
            }

            long currentVal;
            synchronized (idMap) {
                Long val = idMap.get(owner);
                currentVal = (val == null) ? 1 : val + 1;
                idMap.put(owner, currentVal);
            }

            synchronized (affinityGraph) {
                // Add the vertex while synchronized to ensure no interference
                // from the graph pruner.
                LabelVertex vowner = addOrGetVertex(owner);
                // add or update edges between task owner and identities
                for (Map.Entry<Identity, Long> entry : idMap.entrySet()) {
                    Identity ident = entry.getKey();

                    // Our folded graph has no self-loops:  only add an
                    // edge if the identity isn't the owner
                    if (!ident.equals(owner)) {
                        LabelVertex vident = addOrGetVertex(ident);
                        // Check to see if we already have an edge between
                        // the two vertices.  If so, update its weight.
                        WeightedEdge edge =
                                affinityGraph.findEdge(vowner, vident);
                        if (edge == null) {
                            WeightedEdge newEdge = new WeightedEdge();
                            affinityGraph.addEdge(newEdge, vowner, vident);
                            // period info
                            pruneTask.incrementEdge(newEdge);
                        } else {
                            if (currentVal <= entry.getValue()) {
                                edge.incrementWeight();
                                // period info
                                pruneTask.incrementEdge(edge);
                            }
                        }

                    }
                }
            }

            // period info
            pruneTask.updateObjectAccess(objId, owner);
        }

        stats.processingTimeInc(System.currentTimeMillis() - startTime);
    }

    /** {@inheritDoc} */
    public UndirectedGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return Graphs.unmodifiableUndirectedGraph(affinityGraph);
    }

    /** {@inheritDoc} */
    public void enable() {
        if (setEnabledState()) {
            lpa.enable();
        }
    }
    
    /** {@inheritDoc} */
    public void disable() {
        if (setDisabledState()) {
            lpa.disable();
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        if (setShutdownState()) {
            pruneTask.cancel();
            lpa.shutdown();
        }
    }

    /** {@inheritDoc} */
    public LabelVertex getVertex(Identity id) {
        return identMap.get(id);
    }

    /** {@inheritDoc} */
    public LPAAffinityGroupFinder getAffinityGroupFinder() {
        return lpa;
    }

    /**
     * Sets the JMX MBean for this builder.  This is useful for classes
     * which wrap this object.  Note that stats must be set before the
     * first call to updateGraph, or a {@code NullPointerException} will
     * occur.
     *
     * @param stats our JMX information
     */
    public void setStats(AffinityGraphBuilderStats stats) {
        if (stats == null) {
            throw new NullPointerException("null stats");
        }
        this.stats = stats;
    }

    /**
     * Adds a vertex for the given identity to the graph, or retrieve the
     * existing one.
     * @param id the identity
     * @return the graph vertex representing the identity
     */
    private LabelVertex addOrGetVertex(Identity id) {
        LabelVertex v = getVertex(id);
        if (v == null) {
            v = new LabelVertex(id);
            affinityGraph.addVertex(v);
            identMap.put(id, v);
        }
        return v;
    }

    /**
     * Get the task which prunes the graph.  This is useful for testing.
     *
     * @return the runnable which prunes the graph.
     * @throws UnsupportedOperationException if this builder does not support
     *    graph pruning.
     */
    public Runnable getPruneTask() {
        return pruneTask;
    }

    /**
     * The graph pruner.  It runs periodically, and is the only code
     * that removes edges and vertices from the graph.
     */
    private class PruneTask extends TimerTask {
        // The number of snapshots we retain in our moving window.
        // We fill this window of changes by waiting for count snapshots
        // to occur before we start pruning, ensuring our queues contain
        // count items.  This means we cannot dynamically change the
        // length of the window.
        private final int count;
        // The current snapshot count, used to initially fill up our window.
        // We run long enough to fill at least one window before the pruner
        // can begin.
        private int current = 1;

        // The change information we keep for each snapshot.  A new change info
        // object is allocated for each snapshot, and during a snapshot it
        // notes all changes made to this builder's data structures.
        // ObjId -> <Identity -> count times accessed>
        private Map<Object, Map<Identity, Integer>> currentPeriodObject;
        // Edge -> count of times incremented
        private Map<WeightedEdge, Integer> currentPeriodEdgeIncrements;

        // Queues of snapshot information.  As a snapshot time period ends,
        // we add its change info to the back of the appropriate queue.  If
        // we have accumulated enough snapshots in our queues to satisfy our
        // "count" requirement, we also remove the information from the first
        // enqueued info object.
        private final Deque<Map<Object, Map<Identity, Integer>>>
            periodObjectQueue =
                new ArrayDeque<Map<Object, Map<Identity, Integer>>>();
        private final Deque<Map<WeightedEdge, Integer>>
            periodEdgeIncrementsQueue =
                new ArrayDeque<Map<WeightedEdge, Integer>>();

        // A lock to guard all uses of the current period information above
        // and the queues.
        // Specifically, we want to ensure that updates to these structures
        // aren't ones currently being pruned.
        private final Object currentPeriodLock = new Object();

        /**
         * Creates a PruneTask.
         * @param count the number of full snapshots we wish to
         *              retain as live data
         */
        public PruneTask(int count) {
            this.count = count;
            synchronized (currentPeriodLock) {
                addPeriodStructures();
            }
        }

        /**
         * Performs all processing required when a time period has ended.
         */
        public void run() {
            stats.pruneCountInc();

            Map<Object, Map<Identity, Integer>> periodObject;
            Map<WeightedEdge, Integer> periodEdgeIncrements;
            // Note: We want to make sure we don't have snapshots that are so
            // short that we cannot do all our pruning within one.
            synchronized (currentPeriodLock) {
                // Add the data structures for this new period that is just
                // starting.
                addPeriodStructures();
                if (current <= count) {
                    // We're still in our inital time window, and haven't
                    // gathered enough periods yet.
                    current++;
                    return;
                }
                // Remove the earliest snasphot.
                periodObject = periodObjectQueue.removeFirst();
                periodEdgeIncrements = periodEdgeIncrementsQueue.removeFirst();
            }

            long startTime = System.currentTimeMillis();

            // For each object, remove the added access counts
            for (Map.Entry<Object, Map<Identity, Integer>> entry :
                periodObject.entrySet())
            {
                Map<Identity, Long> idMap = objectMap.get(entry.getKey());
                synchronized (idMap) {
                    for (Map.Entry<Identity, Integer> updateEntry :
                         entry.getValue().entrySet())
                    {
                        Identity updateId = updateEntry.getKey();
                        long updateValue = updateEntry.getValue();
                        long newVal = idMap.get(updateId) - updateValue;
                        if (newVal <= 0) {
                            idMap.remove(updateId);
                        } else {
                            idMap.put(updateId, newVal);
                        }
                    }
                    if (idMap.isEmpty()) {
                        objectMap.remove(entry.getKey(), idMap);
                    }
                }
            }

            synchronized (affinityGraph) {
                // For each modified edge in the graph, update weights
                for (Map.Entry<WeightedEdge, Integer> entry :
                     periodEdgeIncrements.entrySet())
                {
                    WeightedEdge edge = entry.getKey();
                    int weight = entry.getValue();
                    if (edge.getWeight() == weight) {
                        Pair<LabelVertex> endpts =
                                affinityGraph.getEndpoints(edge);
                        affinityGraph.removeEdge(edge);
                        for (LabelVertex end : endpts) {
                            if (affinityGraph.degree(end) == 0) {
                                affinityGraph.removeVertex(end);
                                identMap.remove(end.getIdentity());
                            }
                        }
                    } else {
                        edge.addWeight(-weight);
                    }
                }
            }

            stats.processingTimeInc(System.currentTimeMillis() - startTime);
        }

        /**
         * Note that an edge's weight has been incremented.
         * @param edge the edge
         */
        void incrementEdge(WeightedEdge edge) {
            synchronized (currentPeriodLock) {
                int v = currentPeriodEdgeIncrements.containsKey(edge) ?
                         currentPeriodEdgeIncrements.get(edge) : 0;
                v++;
                currentPeriodEdgeIncrements.put(edge, v);
            }
        }

        /**
         * Note that an object has been accessed.
         * @param objId the object
         * @param owner the accessor
         */
        void updateObjectAccess(Object objId, Identity owner) {
            synchronized (currentPeriodLock) {
                Map<Identity, Integer> periodIdMap =
                        currentPeriodObject.get(objId);
                if (periodIdMap == null) {
                    periodIdMap = new HashMap<Identity, Integer>();
                    currentPeriodObject.put(objId, periodIdMap);
                }
                int periodValue = periodIdMap.containsKey(owner) ?
                                  periodIdMap.get(owner) : 0;
                periodValue++;
                periodIdMap.put(owner, periodValue);
            }
        }

        /**
         * Update our queues for this period.
         */
        private void addPeriodStructures() {
            currentPeriodObject =
                    new HashMap<Object, Map<Identity, Integer>>();
            periodObjectQueue.addLast(currentPeriodObject);
            currentPeriodEdgeIncrements =
                    new HashMap<WeightedEdge, Integer>();
            periodEdgeIncrementsQueue.addLast(currentPeriodEdgeIncrements);
        }
    }
}
