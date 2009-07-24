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

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Backing service for the UserGroupManager. This service uses hits, provided
 * by the application, to create affinity groups.
 */
public final class UserGroupService extends AbstractService {

    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.channel";

    /** The property name for the server host. */
    static final String SERVER_HOST_PROPERTY = PKG_NAME + ".server.host";

    /** Group finder server proxy */
    private final UserGroupFinderServer server;

    private final TaskService taskService;

    private final long nodeId;

    private final AtomicLong nextId;

    /** Map Identity -> GroupID */
    private final Map<Identity, Long> associations =
                                        new ConcurrentHashMap<Identity, Long>();

    public UserGroupService(Properties properties,
			    ComponentRegistry systemRegistry,
			    TransactionProxy txnProxy)
        throws Exception
    {
        super(properties, systemRegistry,
              txnProxy, new LoggerWrapper(Logger.getLogger(PKG_NAME)));

        System.out.println("Creating UserGroupService $#####");
        logger.log(Level.CONFIG,
                 "Creating UserGroupService properties:{0}", properties);

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        WatchdogService watchdogService =
                                    txnProxy.getService(WatchdogService.class);

        nodeId = watchdogService.getLocalNodeId();

        // group ids are a the node id in the top 16 bits and incremented by
        // one from there. The risk of duplication is low, and the consequences
        // of a duplicate are minor.
        assert nodeId < (2^16);
        assert nodeId > 0;
        nextId = new AtomicLong(nodeId << 48);

        String host =
                wrappedProps.getProperty(SERVER_HOST_PROPERTY,
                                         wrappedProps.getProperty(
                                               StandardProperties.SERVER_HOST));
        if (host == null) {
            throw new IllegalArgumentException(
                                   "A server host must be specified");
        }
        int port = wrappedProps.getIntProperty(
                    UserGroupFinderServerImpl.SERVER_PORT_PROPERTY,
                    UserGroupFinderServerImpl.DEFAULT_SERVER_PORT, 0, 65535);

        Registry registry = LocateRegistry.getRegistry(host, port);
        server = (UserGroupFinderServer)registry.lookup(
                                UserGroupFinderServerImpl.SERVER_EXPORT_NAME);

        taskService = txnProxy.getService(TaskService.class);
    }

    /**
     * Create a new group and return it's ID.
     *
     * @return a group id
     */
    long createGroup() {
        System.out.println("created group " + nextId.get());
        return nextId.getAndIncrement();
    }

    private int count = 0;  // ack!

    /**
     * Make an association between a client session and a group.
     *
     * TODO groupId <= 0?
     *
     * @param session a client session
     * @param groupId a group id
     */
    void associate(ClientSession session, long groupId) {
        if (session == null) {
            throw new NullPointerException("session can not be null");
        }
        System.out.println("made association between " + session + " and " + groupId);
        associations.put(ClientSessionImpl.getIdentity(session), groupId);
        count++;
        if ((count % 10) == 0) {
            System.out.println("Sending associations");
            taskService.scheduleNonDurableTask(new UpdateServerTask(), false);
        }
    }

    private class UpdateServerTask extends AbstractKernelRunnable {

        UpdateServerTask() {
            super(null);
        }

        @Override
        public void run() throws Exception {
            try {
                server.associations(associations, nodeId);
            } catch (IOException ex) {
                logger.logThrow(Level.SEVERE, ex, "Exception calling server");
            }
        }
    }

    @Override
    protected void doReady() throws Exception {        
    }

    @Override
    protected void doShutdown() {
    }

    @Override
    protected void handleServiceVersionMismatch(Version oldVersion,
                                                Version currentVersion) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
