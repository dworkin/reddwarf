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
import com.sun.sgs.service.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.NodeMapUtil;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import java.util.Collections;
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
    }

    @Override
    public void start() {
        // noop
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "starting coordinator");
        }
    }

    @Override
    public void stop() {
        // noop
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "stopping coordinator");
        }
    }

    // Note that this method will only ever offload a single identity.
    @Override
    public void offload(Node oldNode) throws NoNodesAvailableException {
        GetIdTask task =
                  new GetIdTask(NodeMapUtil.getPartialNodeKey(oldNode.getId()));
        try {
            server.runTransactionally(task);
        } catch (Exception ex) {
            logger.logThrow(Level.WARNING, ex, "Exception getting ID");
        }
        
        IdentityMO idmo = task.getIdmo();

        if (idmo != null) {
            server.moveIdentities(Collections.singleton(idmo.getIdentity()),
                                  oldNode, server.chooseNode());
        }
    }

     class GetIdTask extends AbstractKernelRunnable  {

        /** If !null, the identity we were looking for */
        private IdentityMO idmo = null;

        private final String nodekey;

        GetIdTask(String nodekey) {
	    super("GetIdTask");
            this.nodekey = nodekey;
        }

        public void run() {
            String key = dataService.nextServiceBoundName(nodekey);
            if (key != null && key.contains(nodekey)) {
                idmo = (IdentityMO)dataService.getServiceBinding(key);
            }
        }

        IdentityMO getIdmo() {
            return idmo;
        }
     }

    @Override
    public void shutdown() {
        // nothing to do
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "shutting down coordinator");
        }
    }
}