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

package com.sun.sgs.impl.service.nodemap.coordinator.simple;

import com.sun.sgs.impl.service.nodemap.GroupCoordinator;
import com.sun.sgs.impl.service.nodemap.IdentityMO;
import com.sun.sgs.impl.service.nodemap.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeMapUtil;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple group coordinator which only deals with single identities.
 */
public class SimpleCoordinator implements GroupCoordinator {

    /** Package name for this class. */
    private static final String PKG_NAME =
                            "com.sun.sgs.impl.service.nodemap.affinity.simple";

    protected final NodeMappingServerImpl server;
    protected final ComponentRegistry systemRegistry;
    protected final TransactionProxy txnProxy;
    protected final LoggerWrapper logger;
    protected final DataService dataService;

    private boolean shutdown;

    public SimpleCoordinator(Properties properties,
                             NodeMappingServerImpl server,
                             ComponentRegistry systemRegistry,
                             TransactionProxy txnProxy) {
        this(properties,
             server,
             systemRegistry,
             txnProxy,
             new LoggerWrapper(Logger.getLogger(PKG_NAME + ".coordinator")));
    }

    protected SimpleCoordinator(Properties properties,
                                NodeMappingServerImpl server,
                                ComponentRegistry systemRegistry,
                                TransactionProxy txnProxy,
                                LoggerWrapper logger)
    {
        this.server = server;
        this.systemRegistry = systemRegistry;
        this.txnProxy = txnProxy;
        this.logger = logger;
        dataService = txnProxy.getService(DataService.class);
        shutdown = false;
    }

    @Override
    public void enable() {
        checkShutdown();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "enable coordinator");
        }
    }

    @Override
    public void disable() {
        checkShutdown();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "disable coordinator");
        }
    }

    @Override
    public void offload(Node node) throws NoNodesAvailableException {
        checkShutdown();

        long nodeId = node.getId();

        // Look up each identity on the old node and move it
        String nodekey = NodeMapUtil.getPartialNodeKey(nodeId);
        GetIdOnNodeTask task =
                new GetIdOnNodeTask(dataService, nodekey, logger);

        do {
            checkShutdown();
            try {
                // Find an identity on the node
                server.runTransactionally(task);
            } catch (Exception ex) {
                logger.logThrow(Level.WARNING, ex,
                                "Failed to find identity on node {1}",
                                nodeId);
                break;
            }
            if (task.done()) {
                break;
            }
            server.moveIdentity(task.getId().getIdentity(), node, -1);

        // if the node is alive, only move one id
        } while (!node.isAlive());
    }

    /**
     * Task to find identities on a node.
     */
    private static class GetIdOnNodeTask extends AbstractKernelRunnable {
        /** Set to true when no more identities to be found (or a failure) */
        private boolean done = false;
        /** If !done, the identity we were looking for */
        private IdentityMO idmo = null;

        private final DataService dataService;
        private final String nodekey;
        private final LoggerWrapper logger;

        GetIdOnNodeTask(DataService dataService,
                        String nodekey, LoggerWrapper logger)
        {
	    super("GetIdOnNodeTask for " + nodekey);
            this.dataService = dataService;
            this.nodekey = nodekey;
            this.logger = logger;
        }

        public void run() {
            try {
                String key = dataService.nextServiceBoundName(nodekey);
                done = (key == null || !key.contains(nodekey));
                if (!done) {
                    idmo = (IdentityMO) dataService.getServiceBinding(key);
                }
            } catch (Exception e) {
                done = true;
                logger.logThrow(Level.WARNING, e,
                                "Failed to get key or binding for {0}",
                                nodekey);
            }
        }

        /**
         * Returns true if there are no more identities to be found.
         * @return {@code true} if no more identities could be found for the
         *          node, {@code false} otherwise.
         */
        public boolean done() {
            return done;
        }

        /**
         *  The identity MO retrieved from the data store, or null if
         *  the task has not yet executed or there was an error while
         *  executing.
         * @return the IdentityMO
         */
        public IdentityMO getId() {
            return idmo;
        }
    }

    @Override
    public synchronized void shutdown() {
        checkShutdown();
        shutdown = true;
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "shutting down coordinator");
        }
    }

    protected synchronized void checkShutdown() {
        if (shutdown) throw new IllegalStateException("Cooridinator shutdown");
    }
}