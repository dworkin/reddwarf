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

package com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import
    com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilderStats;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.AffinityGraphBuilderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.WatchdogService;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * A graph builder which builds an affinity graph consisting of identities 
 * as vertices and a single weighted edges representing objects used by both
 * identities. 
 * <p>
 * The data access information naturally forms a bipartite graph, with
 * verticies being either identities or objects, and an edge connecting each
 * identity which has accessed an object.
 * However, we want a graph with vertices for identities, and edges 
 * representing object accesses between identities, so we need the bipartite
 * graph to be folded.  
 * <p>
 * We build the folded graph on the fly by keeping track of which objects have
 * been used by which identities.  Edges between identities are weighted, and
 * represent the number of object accesses the two identities have in common.
 */
public class WeightedGraphBuilder implements DLPAGraphBuilder {
    /** Our property base name. */
    private static final String PROP_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** Our logger. */
    protected static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PROP_NAME));

    /** Map for tracking object-> map of identity-> number accesses
     * (thus we keep track of the number of accesses each identity has made
     * for an object, to aid maintaining weighted edges)
     * Concurrent modifications are protected by locking the affinity graph
     */
    private final ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>>
        objectMap =
           new ConcurrentHashMap<Object, ConcurrentMap<Identity, AtomicLong>>();
    
    /** Our graph of object accesses. */
    private final UndirectedSparseGraph<LabelVertex, WeightedEdge>
        affinityGraph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

    /** Our recorded cross-node accesses.  We keep track of this through
     * conflicts detected in data cache kept across nodes;  when a
     * local node is evicted from the cache because of a request from another
     * node for it, we are told of the eviction.
     * Map of nodes to objects that were evicted to go to that node, with a
     * count.
     */
    private final ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
        conflictMap =
            new ConcurrentHashMap<Long, ConcurrentMap<Object, AtomicLong>>();

    /** The TimerTask which prunes our data structures over time.  As the data
     * structures above are modified, the pruneTask notes the ways they have
     * changed.  Groups of changes are chunked into periods, each the length
     * of the time snapshot (configured at construction time). We
     * periodically remove the changes made in the earliest snapshot.
     */
    private final PruneTask pruneTask;

    /** Our JMX exposed information. */
    private final AffinityGraphBuilderStats stats;

    // Our label propagation algorithm parts
    /** The core server node portion or null if not valid. */
    private final LabelPropagationServer lpaServer;
    /** The app node portion or null if not valid. */
    private final LabelPropagation lpa;

    /**
     * Creates a weighted graph builder.
     * @param col the profile collector
     * @param wdog the watchdog service, used for error reporting
     * @param properties  application properties
     * @param nodeId the local node id
     * @throws Exception if an error occurs
     */
    public WeightedGraphBuilder(ProfileCollector col, WatchdogService wdog,
                                Properties properties, long nodeId)
        throws Exception
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        long snapshot =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        int periodCount = wrappedProps.getIntProperty(
                PERIOD_COUNT_PROPERTY, DEFAULT_PERIOD_COUNT,
                1, Integer.MAX_VALUE);

        // Create the LPA algorithm pieces
        NodeType type =
            NodeType.valueOf(
                properties.getProperty(StandardProperties.NODE_TYPE));
        if (type == NodeType.coreServerNode) {
            lpaServer = new LabelPropagationServer(col, wdog, properties);
            lpa = null;
        } else if (type == NodeType.appNode) {
            lpaServer = null;
            lpa = new LabelPropagation(this, wdog, nodeId, properties);
        } else {
            lpaServer = null;
            lpa = null;
        }
        
        // Create our JMX MBean
        stats = new AffinityGraphBuilderStats(col,
                    affinityGraph, periodCount, snapshot);
        try {
            col.registerMBean(stats, AffinityGraphBuilderMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            // Continue on if we couldn't register this bean, although
            // it's probably a very bad sign
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
        pruneTask = new PruneTask(periodCount);
        Timer pruneTimer = new Timer("AffinityGraphPruner", true);
        pruneTimer.schedule(pruneTask, snapshot, snapshot);
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * This method is called by a single thread but must protect itself
     * from changes to data structures made by the pruner.
     */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        long startTime = System.currentTimeMillis();
        stats.updateCountInc();

        LabelVertex vowner = new LabelVertex(owner);

        // For each object accessed in this task...
        for (AccessedObject obj : detail.getAccessedObjects()) {
            Object objId = obj.getObjectId();

            // find the identities that have already used this object
            ConcurrentMap<Identity, AtomicLong> idMap = objectMap.get(objId);
            if (idMap == null) {
                // first time we've seen this object
                ConcurrentMap<Identity, AtomicLong> newMap =
                        new ConcurrentHashMap<Identity, AtomicLong>();
                idMap = objectMap.putIfAbsent(objId, newMap);
                if (idMap == null) {
                    idMap = newMap;
                }
            }
            AtomicLong value = idMap.get(owner);
            if (value == null) {
                AtomicLong newVal = new AtomicLong();
                value = idMap.putIfAbsent(owner, newVal);
                if (value == null) {
                    value = newVal;
                }
            }
            long currentVal = value.incrementAndGet();

            synchronized (affinityGraph) {
                affinityGraph.addVertex(vowner);
                // add or update edges between task owner and identities
                for (Map.Entry<Identity, AtomicLong> entry : idMap.entrySet()) {
                    Identity ident = entry.getKey();

                    // Our folded graph has no self-loops:  only add an
                    // edge if the identity isn't the owner
                    if (!ident.equals(owner)) {
                        LabelVertex vident = new LabelVertex(ident);
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
                            AtomicLong otherValue = entry.getValue();
                            if (currentVal <= otherValue.get()) {
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
    public Runnable getPruneTask() {
        return pruneTask;
    }

    /** {@inheritDoc} */
    public UndirectedSparseGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return affinityGraph;
    }

    /** {@inheritDoc} */
    public ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>>
            getObjectUseMap()
    {
        return objectMap;
    }

    /** {@inheritDoc} */
    public ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
            getConflictMap()
    {
        return conflictMap;
    }

    /** {@inheritDoc} */
    public void shutdown() {
        pruneTask.cancel();
        if (lpaServer != null) {
            lpaServer.shutdown();
        }
        if (lpa != null) {
            lpa.shutdown();
        }
    }

    /** {@inheritDoc} */
    public AffinityGroupFinder getAffinityGroupFinder() {
        return lpaServer;
    }
    
    /**
     * This will be the implementation of our conflict detection listener.
     * <p>
     * Note that forUpdate is currently not used.
     * 
     * @param objId the object that was evicted
     * @param nodeId the node that caused the eviction
     * @param forUpdate {@code true} if this eviction was for an update,
     *                  {@code false} if it was for read only access
     */
    public void noteConflictDetected(Object objId, long nodeId,
                                     boolean forUpdate)
    {
        if (objId == null) {
            throw new NullPointerException("objId must not be null");
        }
        ConcurrentMap<Object, AtomicLong> objMap = conflictMap.get(nodeId);
        if (objMap == null) {
            ConcurrentMap<Object, AtomicLong> newMap =
                    new ConcurrentHashMap<Object, AtomicLong>();
            objMap = conflictMap.putIfAbsent(nodeId, newMap);
            if (objMap == null) {
                objMap = newMap;
            }
        }
        AtomicLong count = objMap.get(objId);
        if (count == null) {
            AtomicLong newCount = new AtomicLong();
            count = objMap.putIfAbsent(objId, newCount);
            if (count == null) {
                count = newCount;
            }
        }
        count.incrementAndGet();
        pruneTask.updateConflict(objId, nodeId);
    }

    /** {@inheritDoc} */
    public void removeNode(long nodeId) {
        conflictMap.remove(nodeId);
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
        private int current = 1;

        // The change information we keep for each snapshot.  A new change info
        // object is allocated for each snapshot, and during a snapshot it
        // notes all changes made to this builder's data structures.
        // ObjId -> <Identity -> count times accessed>
        private Map<Object, Map<Identity, Integer>> currentPeriodObject;
        // Edge -> count of times incremented
        private Map<WeightedEdge, Integer> currentPeriodEdgeIncrements;
        // NodeId -> <ObjId, count times conflicted>
        // Note that the conflict count is not currently used
        private Map<Long, Map<Object, Integer>> currentPeriodConflicts;

        // A lock to guard all uses of the current period information above.
        // Specifically, we want to ensure that updates to these structures
        // aren't ones currently being pruned.
        private final Object currentPeriodLock = new Object();

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
        private final Deque<Map<Long, Map<Object, Integer>>>
            periodConflictQueue =
                new ArrayDeque<Map<Long, Map<Object, Integer>>>();

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
            }

            long startTime = System.currentTimeMillis();
            
            // Remove the earliest snasphot.
            Map<Object, Map<Identity, Integer>>
                periodObject = periodObjectQueue.remove();
            Map<WeightedEdge, Integer> 
                periodEdgeIncrements = periodEdgeIncrementsQueue.remove();
            Map<Long, Map<Object, Integer>>
                periodConflicts = periodConflictQueue.remove();

            // For each object, remove the added access counts
            for (Map.Entry<Object, Map<Identity, Integer>> entry :
                periodObject.entrySet())
            {
                ConcurrentMap<Identity, AtomicLong> idMap =
                        objectMap.get(entry.getKey());
                for (Map.Entry<Identity, Integer> updateEntry :
                     entry.getValue().entrySet())
                {
                    Identity updateId = updateEntry.getKey();
                    long updateValue = updateEntry.getValue();
                    AtomicLong val = idMap.get(updateId);
                    // correct? should be using compareAndSet?
                    val.addAndGet(-updateValue);
                    if (val.get() <= 0) {
                        idMap.remove(updateId);
                    }
                }
                if (idMap.isEmpty()) {
                    objectMap.remove(entry.getKey());
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
                            }
                        }
                    } else {
                        edge.addWeight(-weight);
                    }
                }
            }

            // For each conflict, update values
            for (Map.Entry<Long, Map<Object, Integer>> entry :
                 periodConflicts.entrySet())
            {
                Long nodeId = entry.getKey();
                ConcurrentMap<Object, AtomicLong> objMap =
                        conflictMap.get(nodeId);
                // If the node went down, we might have removed the entry
                if (objMap != null) {
                    for (Map.Entry<Object, Integer> updateEntry :
                          entry.getValue().entrySet())
                    {
                        Object objId = updateEntry.getKey();
                        Integer periodVal = updateEntry.getValue();
                        AtomicLong conflictVal = objMap.get(objId);
                        long oldVal;
                        long newVal;
                        do {
                            oldVal = conflictVal.get();
                            newVal = oldVal - periodVal;
                        } while (!conflictVal.compareAndSet(oldVal, newVal));

                        if (newVal <= 0) {
                            // This could remove a just incremented value!
                            objMap.remove(objId);
                        }
                    }
                    if (objMap.isEmpty()) {
                        conflictMap.remove(nodeId);
                    }
                }
            }
            stats.processingTimeInc(System.currentTimeMillis() - startTime);
        }

        /**
         * Note that an edge's weight has been incremented.
         * Called by a single thread.
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
         * Called by a single thread.
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
         * Note that a data cache conflict has been detected.
         * @param objId the objId of the object causing the conflict
         * @param nodeId the node ID of the node we were in conflict with
         */
        void updateConflict(Object objId, long nodeId) {
            synchronized (currentPeriodLock) {
                Map<Object, Integer> periodObjMap =
                        currentPeriodConflicts.get(nodeId);
                if (periodObjMap == null) {
                    periodObjMap = new HashMap<Object, Integer>();
                    currentPeriodConflicts.put(nodeId, periodObjMap);
                }
                int periodValue = periodObjMap.containsKey(objId) ?
                                  periodObjMap.get(objId) : 0;
                periodValue++;
                periodObjMap.put(objId, periodValue);
            }
        }

        /**
         * Update our queues for this period.
         */
        private void addPeriodStructures() {
            currentPeriodObject =
                    new HashMap<Object, Map<Identity, Integer>>();
            periodObjectQueue.add(currentPeriodObject);
            currentPeriodEdgeIncrements =
                    new HashMap<WeightedEdge, Integer>();
            periodEdgeIncrementsQueue.add(currentPeriodEdgeIncrements);
            currentPeriodConflicts =
                    new HashMap<Long, Map<Object, Integer>>();
            periodConflictQueue.add(currentPeriodConflicts);
        }
    }
}
