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

package com.sun.sgs.impl.service.nodemap.affinity.dgb;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import
    com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilderStats;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleLabelPropagation;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
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
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class DistGraphBuilderServerImpl 
    implements DistGraphBuilderServer, AffinityGraphBuilder, AffinityGroupFinder
{
    /** Our property base name. */
    private static final String PROP_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** The property name for the server port. */
    public static final String SERVER_PORT_PROPERTY =
            PROP_NAME + ".server.port";
    /** Our logger. */
    protected static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PROP_NAME));

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

    /** Generation number for our returned groups. */
    private final AtomicLong generation = new AtomicLong();

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
        transactionScheduler =
	    systemRegistry.getComponent(TransactionScheduler.class);
	this.txnProxy = txnProxy;
	taskOwner = txnProxy.getCurrentOwner();
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        ProfileCollector col =
                systemRegistry.getComponent(ProfileCollector.class);
        // Create our backing graph builder.  We wrap this object so we
        // can return a different type from findAffinityGroups.
        builder = new SingleGraphBuilder(properties, systemRegistry,
                                         txnProxy, false);
 
        // Create our group finder and graph builder JMX MBeans
        AffinityGroupFinderStats stats =
                new AffinityGroupFinderStats(this, col, -1);

        int periodCount = wrappedProps.getIntProperty(
                PERIOD_COUNT_PROPERTY, DEFAULT_PERIOD_COUNT,
                1, Integer.MAX_VALUE);
        long snapshot =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
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
        builder.updateGraph(owner, objIds);
    }

    // Implement AffinityGraphBuilder

    /** {@inheritDoc} */
    public void shutdown() {
        // This method is in both AffinityGraphBuilder and AffinityGroupFinder
        builder.shutdown();
        lpa.shutdown();
    }

    /** {@inheritDoc} */
    public AffinityGroupFinder getAffinityGroupFinder() {
        return this;
    }

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        throw new UnsupportedOperationException("Unexpected direct update");
    }

    /** {@inheritDoc} */
    public UndirectedSparseGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        return builder.getAffinityGraph();
    }

    /** {@inheritDoc} */
    public Runnable getPruneTask() {
        return builder.getPruneTask();
    }

    // Implement AffinityGroupFinder

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
    public Set<AffinityGroup> findAffinityGroups() {
        long gen = generation.incrementAndGet();
        Set<AffinityGroup> groups = lpa.findAffinityGroups();
        // Need to translate each group into a relocating affinity group
        // Create our final return values
        Set<AffinityGroup> retVal = new HashSet<AffinityGroup>();
        for (AffinityGroup ag : groups) {
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
