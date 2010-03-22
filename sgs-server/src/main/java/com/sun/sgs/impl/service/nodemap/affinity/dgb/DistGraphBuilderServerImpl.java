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

package com.sun.sgs.impl.service.nodemap.affinity.dgb;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import
   com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderFailedException;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import
   com.sun.sgs.impl.service.nodemap.affinity.graph.AbstractAffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import
    com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilderStats;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleLabelPropagation;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.management.AffinityGraphBuilderMXBean;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.UnknownIdentityException;
import edu.uci.ics.jung.graph.UndirectedGraph;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.management.JMException;

/**
 * The server side of a distributed graph builder for label propagation.
 *
 * It builds a single graph representing all information for the entire
 * system using the single node graph builder, and uses a single node label
 * propagation implementation to process the graph.
 *
 * This LPA implementation is expected to be useful for simple multi-node
 * testing.  It may have scalability problems due to the amount of data
 * being sent from each node, and the size of the affinity graph and
 * related data structures.  It has no dependencies on new multi-node parts
 * of the system.
 */
public class DistGraphBuilderServerImpl extends AbstractAffinityGraphBuilder
    implements DistGraphBuilderServer, AffinityGraphBuilder,
               LPAAffinityGroupFinder
{
    /** The property name for the server port. */
    public static final String SERVER_PORT_PROPERTY =
            PROP_BASE + ".server.port";

    /** The default value of the server port. */
    public static final int DEFAULT_SERVER_PORT = 44537;

    /** The name we export ourselves under. */
    public static final String SERVER_EXPORT_NAME = 
            "DistributedGraphBuilderServer";

    /** The exporter for this server. */
    private final Exporter<DistGraphBuilderServer> exporter;

    /** Our backing builder. */
    private final SingleGraphBuilder builder;

    /** Our label propagation algorithm. */
    private final SingleLabelPropagation lpa;

    /** The transaction scheduler. */
    private final TransactionScheduler transactionScheduler;

    /** The task owner. */
    private final Identity taskOwner;

    /** Our transaction proxy. */
    private final TransactionProxy txnProxy;

    /** Our JMX exposed information.  The group finder stats is held
     * in {@code lpa}.
     */
    private final AffinityGraphBuilderStats builderStats;

    /**
     * Creates a distributed graph builder server.
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @param properties  application properties
     * @param nodeId the core server node id
     * @throws Exception if an error occurs
     */
    DistGraphBuilderServerImpl(ComponentRegistry systemRegistry,
                               TransactionProxy txnProxy,
                               Properties properties,
                               long nodeId)
            throws Exception
    {
        super(properties);
        transactionScheduler =
	    systemRegistry.getComponent(TransactionScheduler.class);
	this.txnProxy = txnProxy;
	taskOwner = txnProxy.getCurrentOwner();

        ProfileCollector col =
                systemRegistry.getComponent(ProfileCollector.class);
        // Create our backing graph builder.  We wrap this object so we
        // can return a different type from findAffinityGroups.
        builder = new SingleGraphBuilder(properties, systemRegistry,
                                         txnProxy, false);
 
        // Create our group finder and graph builder JMX MBeans
        AffinityGroupFinderStats stats =
                new AffinityGroupFinderStats(this, col, -1);
        builderStats = new AffinityGraphBuilderStats(col,
                                                     builder.getAffinityGraph(),
                                                     periodCount, snapshot);
        // We must set the stats before exporting ourself!
        builder.setStats(builderStats);
        try {
            col.registerMBean(stats, AffinityGroupFinderMXBean.MXBEAN_NAME);
            col.registerMBean(builderStats,
                                     AffinityGraphBuilderMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            // Continue on if we couldn't register this bean, although
            // it's probably a very bad sign
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
        // Create the LPA algorithm, telling it to use our group finder MBean.
        lpa = new SingleLabelPropagation(this, col, properties, stats);

        int requestedPort = wrappedProps.getIntProperty(
                SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
        // Export ourself.
        exporter =
            new Exporter<DistGraphBuilderServer>(DistGraphBuilderServer.class);
        exporter.export(this, SERVER_EXPORT_NAME, requestedPort);
    }

    // Implement DistGraphBuilderServer

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, Object[] objIds) {
        checkForShutdownState();
        builder.updateGraph(owner, objIds);
    }

    // Implement LPAAffinityGraphBuilder

    /** {@inheritDoc} */
    public void disable() {
        if (setDisabledState()) {
            // We don't tell our clients, so they will still be sending
            // graph updates via RMI.  But our builder will discard them.
            // A better solution would be to have clients register with this
            // server, so they could be called here -- that's a heavy-weight
            // option.
            // For now, can't entirely disable this variation.
            builder.disable();
            lpa.disable();
        }
    }

    /** {@inheritDoc} */
    public void enable() {
        if (setEnabledState()) {
            builder.enable();
            lpa.enable();
        }
    }
    /** {@inheritDoc} */
    public void shutdown() {
        // This method is in both AffinityGraphBuilder and AffinityGroupFinder
        if (setShutdownState()) {
            builder.shutdown();
            lpa.shutdown();
        }
    }

    /** {@inheritDoc} */
    public LPAAffinityGroupFinder getAffinityGroupFinder() {
        return this;
    }

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        throw new UnsupportedOperationException("Unexpected direct update");
    }

    /** {@inheritDoc} */
    public UndirectedGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return builder.getAffinityGraph();
    }

    /** {@inheritDoc} */
    public LabelVertex getVertex(Identity id) {
        return builder.getVertex(id);
    }

    /**
     * Get the task which prunes the graph.  This is useful for testing.
     *
     * @return the runnable which prunes the graph.
     * @throws UnsupportedOperationException if this builder does not support
     *    graph pruning.
     */
    public Runnable getPruneTask() {
        return builder.getPruneTask();
    }

    // Implement LPAAffinityGroupFinder

    /**
     * {@inheritDoc}
     * <p>
     * The returned groups contain node assignment information for the
     * identities.  This information is looked up from the node mapping service.
     * Alternatively, we could create a new graph node type and track this
     * information based on which application node called updateGraph.  While
     * this would be some work, it might be worthwhile to implement if we
     * find this LPA implementation is useful in deployed systems.
     */
    public NavigableSet<RelocatingAffinityGroup> findAffinityGroups()
            throws AffinityGroupFinderFailedException
    {
        checkForDisabledOrShutdownState();

        Set<RelocatingAffinityGroup> groups = lpa.findAffinityGroups();
        // Need to translate each group into a relocating affinity group
        // Create our final return values.  This is much like what the
        // single group finder needed to do, but we ask the node mapping
        // service for the real node assignments.
        NavigableSet<RelocatingAffinityGroup> retVal =
                new TreeSet<RelocatingAffinityGroup>();
        for (AffinityGroup ag : groups) {
            long gen = ag.getGeneration();
            Map<Identity, Long> idMap = new HashMap<Identity, Long>();
            for (Identity id : ag.getIdentities()) {
                try {
                    GetNodeIdTask getNodeIdTask = new GetNodeIdTask(id);
                    runTransactionally(getNodeIdTask);
                    idMap.put(id, getNodeIdTask.getNodeId());
                } catch (UnknownIdentityException e) {
                    // just leave this one out
                } catch (Exception e) {
                    // We don't know what the assignment is.
                    logger.log(Level.INFO,
                               "Unknown node assignment for identity {0}", id);
                    idMap.put(id, Long.valueOf(-1));
                }
            }
            retVal.add(new RelocatingAffinityGroup(ag.getId(), idMap, gen));
        }
        return retVal;
    }

    /**
     *  Run the given task synchronously, and transactionally, retrying
     *  if the exception is of type {@code ExceptionRetryStatus}.
     * @param task the task
     */
    private void runTransactionally(KernelRunnable task) throws Exception {
        transactionScheduler.runTask(task, taskOwner);
    }

    /**
     * A transactional task to obtain the node assignment for a given identity.
     */
    private class GetNodeIdTask extends AbstractKernelRunnable {

	private final Identity id;
	private volatile long nodeId = -1;

	GetNodeIdTask(Identity id) {
	    super(null);
	    this.id = id;
	}

	public void run() throws Exception {
	    nodeId = txnProxy.getService(NodeMappingService.class).
                         getNode(id).getId();
	}

	long getNodeId() {
	    return nodeId;
	}
    }
}
