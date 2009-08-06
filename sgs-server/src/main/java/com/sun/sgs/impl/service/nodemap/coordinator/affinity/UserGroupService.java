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

package com.sun.sgs.impl.service.nodemap.coordinator.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Backing service for the UserGroupManager. This service collects associations
 * made on this node and exports them through the {@link UserGroupSource}
 * interface.
 */
public final class UserGroupService extends AbstractService
                                    implements NodeMappingListener
{
    /** The package name. */
    private static final String PKG_NAME =
                    "com.sun.sgs.impl.service.nodemap.coordinator.affinity";

    /** The property name for the server host. */
    static final String SERVER_HOST_PROPERTY = PKG_NAME + ".server.host";

    /** The property name for the group service client port. */
    private static final String CLIENT_PORT_PROPERTY =PKG_NAME + ".client.port";

    /** The default value of the client port. */
    private static final int DEFAULT_CLIENT_PORT = 0;

    /** Group finder server proxy */
    private final UserGroupFinderServer server;

    /** The idea of the local node */
    private final long nodeId;

    /** Counter for creating unique ids */
    private final AtomicLong nextId;

    /** Map Identity -> GroupID */
    private final Map<Identity, Long> associations =
                                                new HashMap<Identity, Long>();

    private boolean running = false;
    private boolean shutdown = false;

    public UserGroupService(Properties properties,
			    ComponentRegistry systemRegistry,
			    TransactionProxy txnProxy)
        throws Exception
    {
        super(properties, systemRegistry, txnProxy,
              new LoggerWrapper(Logger.getLogger(PKG_NAME)));

        logger.log(Level.CONFIG, "Creating UserGroupService service");

        WatchdogService watchdogService =
                                    txnProxy.getService(WatchdogService.class);

        nodeId = watchdogService.getLocalNodeId();

        // TODO - check the math, also perhaps make group id > 0?
        // group ids are a the node id in the top 16 bits and incremented by
        // one from there. The risk of duplication is low, and the consequences
        // of a duplicate are minor.
        assert nodeId < (2^16);
        assert nodeId > 0;
        nextId = new AtomicLong(nodeId << 48);

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        String host =
                wrappedProps.getProperty(SERVER_HOST_PROPERTY,
                                         wrappedProps.getProperty(
                                               StandardProperties.SERVER_HOST));
        if (host == null) {
            throw new IllegalArgumentException(
                                   "A server host must be specified");
        }
        int serverPort = wrappedProps.getIntProperty(
                    UserGroupFinderServerImpl.SERVER_PORT_PROPERTY,
                    UserGroupFinderServerImpl.DEFAULT_SERVER_PORT, 0, 65535);

        Registry registry = LocateRegistry.getRegistry(host, serverPort);
        server = (UserGroupFinderServer)registry.lookup(
                                UserGroupFinderServerImpl.SERVER_EXPORT_NAME);

        int clientPort = wrappedProps.getIntProperty(
		CLIENT_PORT_PROPERTY, DEFAULT_CLIENT_PORT, 0, 65535);

        logger.log(Level.CONFIG,
                   "UserGroupService, server host: {0}, port: {1,number,#}, " +
                   "requested client port: {2,number,#}",
                   host, serverPort, clientPort);

        UserGroupSourceImpl sourceImpl = new UserGroupSourceImpl();
	Exporter<UserGroupSource> exporter =
                        new Exporter<UserGroupSource>(UserGroupSource.class);
	int port = exporter.export(sourceImpl, clientPort);
        if (clientPort == 0) {
            logger.log(Level.CONFIG, "Client port is {0,number,#}", port);
        }

	UserGroupSource  sourceProxy = exporter.getProxy();
        server.registerUserGroupSource(sourceProxy, nodeId);

        // register for identity mapping updates
        txnProxy.getService(NodeMappingService.class).
                                                addNodeMappingListener(this);
    }

    /**
     * Create a new group and return it's ID.
     *
     * @return a group id
     */
    long createGroup() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "created group {0}", nextId.get());
        }
        return nextId.getAndIncrement();
    }

    /**
     * Make an association between an identity and a group.
     *
     * @param identity an identity
     * @param groupId a group id
     */
    synchronized void associate(Identity identity, long groupId) {
        if (identity == null) {
            throw new NullPointerException("identity can not be null");
        }
        if (running) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "user requested association between {0} and {1}",
                           identity, groupId);
            }
            associations.put(identity, groupId);
        }
    }

    /**
     * Make an association between two identities.
     *
     * @param identity1 an identity
     * @param identity2 an identity
     */
    synchronized void associate(Identity identity1, Identity identity2) {
        if ((identity1 == null) || (identity2 == null)) {
            throw new NullPointerException("identity can not be null");
        }
        if (running) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "user requested association between {0} and {1}",
                           identity1, identity2);
            }
            Long groupId = associations.get(identity1);

            // if id1 is in a group, use that
            if (groupId != null) {
                associations.put(identity2, groupId);
            } else {
                groupId = associations.get(identity2);

                // if id2 is in a group, use that
                if (groupId == null) {
                    associations.put(identity1, groupId);
                } else {
                    // no one is in a group, create a new one and put both in
                    groupId = createGroup();
                    associations.put(identity1, groupId);
                    associations.put(identity2, groupId);
                }
            }
        }
    }

    // TODO - inline?
    private synchronized Map<Identity, Long> getAssociations() {
        if (!running) {
            throw new IllegalStateException("service not started");
        }
        return associations;
    }


    private synchronized void start() {
        if (shutdown) {
            throw new IllegalStateException("service shutdown");
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "starting service");
        }
        running = true;
        associations.clear();
    }

    private synchronized void stop() {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "stopping service");
        }
        running = true;
        associations.clear();
    }

    /**
     * Proxy to receive requests from the server.
     */
    private final class UserGroupSourceImpl implements UserGroupSource {

        @Override
        public Map<Identity, Long> getAssociations() throws IOException {
            return UserGroupService.this.getAssociations();
        }

        @Override
        public void start() throws IOException {
            UserGroupService.this.start();
        }

        @Override
        public void stop() throws IOException {
            UserGroupService.this.stop();
        }
    }

    /* --- Implementing NodeMappingListener --- */

    @Override
    public void mappingAdded(Identity identity, Node oldNode) {
        // no op
    }

    @Override
    public synchronized void mappingRemoved(Identity identity, Node newNode) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "identity {0} removed from associations",
                       identity);
        }
        associations.remove(identity);
    }

    /* -- From AbstractService -- */

    @Override
    protected void doReady() throws Exception {
    }

    @Override
    protected void doShutdown() {
        shutdown = true;
        stop();
    }

    @Override
    protected void handleServiceVersionMismatch(Version oldVersion,
                                                Version currentVersion) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
