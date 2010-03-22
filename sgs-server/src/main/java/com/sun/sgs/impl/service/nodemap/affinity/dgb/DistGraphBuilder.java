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
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import
   com.sun.sgs.impl.service.nodemap.affinity.graph.AbstractAffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import edu.uci.ics.jung.graph.UndirectedGraph;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Properties;
import java.util.logging.Level;

/**
 * The portion of the distributed affinity graph builder which resides on
 * a local node.  This code forwards graph information to its server,
 * which builds a single large graph for all information in the system.
 * <p>
 * If the server cannot be contacted, we report the failure to the watchdog.
 * <p>
 * The following properties are supported:
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.server.host
 *	</b></code><br>
 *	<i>Default:</i> the value of the {@code com.sun.sgs.server.host}
 *	property, if present, or {@code localhost} if this node is starting the
 *      server <br>
 *
 * <dd style="padding-top: .5em">The name of the host running the {@code
 *	NodeMappingServer}. <p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44537}
 *
 * <dd style="padding-top: .5em">The network port for the {@code
 *	LabelPropagationServer}.  This value must be no less than {@code 0} and
 *      no greater than {@code 65535}. <p>
 * 
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.snapshot.period
 *	</b></code><br>
 *	<i>Default:</i> {@code 300000} (5 minutes)<br>
 *
 * <dd style="padding-top: .5em">The amount of time, in milliseconds, for
 *      each snapshot of retained data.  Older snapshots are discarded as
 *      time goes on. A longer snapshot period gives us more history, but
 *      also longer compute times to use that history, as more data must
 *      be processed.<p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.snapshot.count
 *	</b></code><br>
 *	<i>Default:</i> {@code 1}
 *
 * <dd style="padding-top: .5em">The number of snapshots to retain.  A
 *       larger value means more history will be retained.  Using a smaller
 *       snapshot period with a larger count means more total history will be
 *       retained, with a smaller amount discarded at the start of each
 *       new snapshot.<p>
 * </dl>
 */
public class DistGraphBuilder extends AbstractAffinityGraphBuilder 
        implements AffinityGraphBuilder
{
    /** The property name for the server host. */
    private static final String SERVER_HOST_PROPERTY =
            PROP_BASE + ".server.host";

    /** The default number of IO task retries **/
    private static final int DEFAULT_MAX_IO_ATTEMPTS = 5;
    /** The default time interval to wait between IO task retries **/
    private static final int DEFAULT_RETRY_WAIT_TIME = 100;

    /** The time (in milliseconds) to wait between retries for IO
     * operations.
     */
    private final int retryWaitTime;

    /** The maximum number of retry attempts for IO operations. */
    private final int maxIoAttempts;

    /** The remote server, or null if we're on the core server node. */
    private final DistGraphBuilderServer server;
    /** The server implementation, or null if we're on an app node. */
    private final DistGraphBuilderServerImpl serverImpl;

    /** The watchdog service. */
    private final WatchdogService watchdogService;
    /** Our local node id. */
    private final long localNodeId;

    /**
     * Creates the client side of a distributed graph builder.
     * @param properties the properties for configuring this builder
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @throws Exception if an error occurs
     */
    public DistGraphBuilder(Properties properties,
                            ComponentRegistry systemRegistry,
                            TransactionProxy txnProxy)
        throws Exception
    {
        super(properties);

        watchdogService = txnProxy.getService(WatchdogService.class);

        retryWaitTime = wrappedProps.getIntProperty(
                AbstractService.IO_TASK_WAIT_TIME_PROPERTY,
                DEFAULT_RETRY_WAIT_TIME, 0, Integer.MAX_VALUE);
        maxIoAttempts = wrappedProps.getIntProperty(
                AbstractService.IO_TASK_RETRIES_PROPERTY,
                DEFAULT_MAX_IO_ATTEMPTS, 0, Integer.MAX_VALUE);

        DataService dataService = txnProxy.getService(DataService.class);
        localNodeId = dataService.getLocalNodeId();

        NodeType nodeType =
                wrappedProps.getEnumProperty(StandardProperties.NODE_TYPE,
                                             NodeType.class,
                                             NodeType.singleNode);
        if (nodeType == NodeType.coreServerNode) {
            serverImpl = 
                new DistGraphBuilderServerImpl(systemRegistry, txnProxy,
                                               properties, localNodeId);
            server = null;
        } else {
            String host = wrappedProps.getProperty(SERVER_HOST_PROPERTY,
                            wrappedProps.getProperty(
                                StandardProperties.SERVER_HOST));
            if (host == null) {
                // None specified, use local host
                host = InetAddress.getLocalHost().getHostName();
            }
            int port = wrappedProps.getIntProperty(
                    DistGraphBuilderServerImpl.SERVER_PORT_PROPERTY,
                    DistGraphBuilderServerImpl.DEFAULT_SERVER_PORT, 0, 65535);
            // Look up our server
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (DistGraphBuilderServer) registry.lookup(
                             DistGraphBuilderServerImpl.SERVER_EXPORT_NAME);
            serverImpl = null;
        }
    }

    /** {@inheritDoc} */
    public void updateGraph(final Identity owner, AccessedObjectsDetail detail)
    {
        checkForShutdownState();
        if (state == State.DISABLED) {
            return;
        }
        final Object[] ids = new Object[detail.getAccessedObjects().size()];
        int index = 0;
        for (AccessedObject access : detail.getAccessedObjects()) {
            ids[index++] = access.getObjectId();
        }
        runIoTask(new IoRunnable() {
                    public void run() throws IOException {
                        server.updateGraph(owner, ids);
                    } }, localNodeId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation will always throw an
     * {@code UnsupportedOperationException}, because the graph is not
     * held on the local node.
     */
    public UndirectedGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        throw new UnsupportedOperationException(
                "Cannot obtain the affinity graph from a local node");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation will always throw an
     * {@code UnsupportedOperationException}, because the graph is not
     * held on the local node.
     */
    public LabelVertex getVertex(Identity id) {
        throw new UnsupportedOperationException(
                "Cannot obtain the affinity graph from a local node");
    }


    /** {@inheritDoc} */
    public void enable() {
        if (setEnabledState()) {
            if (serverImpl != null) {
                serverImpl.enable();
            }
        }
    }

    /** {@inheritDoc} */
    public void disable() {
        if (setDisabledState()) {
            if (serverImpl != null) {
                serverImpl.disable();
            }
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        if (setShutdownState()) {
            if (serverImpl != null) {
                serverImpl.shutdown();
            }
        }
    }

    /** {@inheritDoc} */
    public LPAAffinityGroupFinder getAffinityGroupFinder() {
        return serverImpl;
    }

    /**
     * Executes the specified {@code ioTask} by invoking its {@link
     * IoRunnable#run run} method. If the specified task throws an
     * {@code IOException}, this method will retry the task for a fixed
     * number of times. The method will stop retrying if the node with
     * the given {@code nodeId} is no longer alive. The number of retries
     * and the wait time between retries are configurable properties.
     * <p>
     * This is much the same as the like method in AbstractService, except
     * we don't bother to check for a transactional context (we won't be in
     * one), and we use the watchdog's non-transactional call to find out
     * if the local node is alive.  We cannot use the AbstractService version
     * because we are not an AbstractService.  It may be useful to refactor
     * that method into a static method somewhere.
     * 
     * @param ioTask a task with IO-related operations
     * @param nodeId the node that is the target of the IO operations
     */
    private void runIoTask(IoRunnable ioTask, long nodeId) {
        int maxAttempts = maxIoAttempts;
        do {
            try {
                ioTask.run();
                return;
            } catch (IOException e) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.logThrow(Level.FINEST, e,
                            "IoRunnable {0} throws", ioTask);
                }
                if (maxAttempts-- == 0) {
                    logger.logThrow(Level.WARNING, e,
                            "A communication error occured while running an" +
                            "IO task. Reporting node {0} as failed.", nodeId);

                    // Report failure of remote node since are
                    // having trouble contacting it
                    watchdogService.
                            reportFailure(nodeId, this.getClass().toString());

                    break;
                }
                try {
                    // TBD: what back-off policy do we want here?
                    Thread.sleep(retryWaitTime);
                } catch (InterruptedException ie) {
                }
            }
        } while (watchdogService.isLocalNodeAliveNonTransactional());
    }
}
