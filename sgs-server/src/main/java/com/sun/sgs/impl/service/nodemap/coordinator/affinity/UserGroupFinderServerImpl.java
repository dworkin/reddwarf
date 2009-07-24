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
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Server side implementation of component that finds groups based on hints
 * provided by the application.
 */
public class UserGroupFinderServerImpl implements GroupFinder,
                                                  UserGroupFinderServer
{
    /** Package name for this class. */
    private static final String PKG_NAME =
                        "com.sun.sgs.impl.service.nodemap.coordinator.affinity";
    
    /** The property name for the server port. */
    static final String SERVER_PORT_PROPERTY = PKG_NAME + ".server.port";

    /** The default value of the server port. */
    // XXX:  does the exporter allow all servers to use the same port?
    static final int DEFAULT_SERVER_PORT = 44540;

    /** The name we export ourselves under. */
    static final String SERVER_EXPORT_NAME = "UserGroupFinderServer";

    private final AffinityGroupCoordinator coordinator;

    private final TaskScheduler taskScheduler;
    private final Identity taskOwner;

    private final Exporter<UserGroupFinderServer> exporter;

    private Map<Long, AffinityGroup> groups = null;

    private RecurringTaskHandle finderTask = null;

    UserGroupFinderServerImpl(Properties properties,
                              AffinityGroupCoordinator coordinator,
                              ComponentRegistry systemRegistry,
                              TransactionProxy txnProxy)
        throws Exception
    {
        this.coordinator = coordinator;
        this.taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();

        System.out.println("*** constructing UserGroupFinderServerImpl ***");

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        int requestedPort = wrappedProps.getIntProperty(
                SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);

        // Export ourselves.  At this point, this object is public.
        exporter =
               new Exporter<UserGroupFinderServer>(UserGroupFinderServer.class);
        exporter.export(this, SERVER_EXPORT_NAME, requestedPort);
    }

    /* --- Implement UserGroupFinderServer -- */

    @Override
    public synchronized void associations(Map<Identity, Long> associations,
                                          long nodeId)
        throws IOException
    {
        System.out.println("received " + associations.size() + " associations from service on node " + nodeId);
        if (groups == null) return;

        for (Map.Entry<Identity, Long> entry : associations.entrySet()) {
            AffinityGroup group = groups.get(entry.getValue());

            if (group == null) {
                group = new AffinityGroup(entry.getValue());
                groups.put(entry.getValue(), group);
            }
            group.add(entry.getKey(), nodeId);
        }
    }

    private synchronized void updateCoordinator() {
        System.out.println("updating coordinator, groups = " + groups);
        if ((groups != null) && !groups.isEmpty()) {

            System.out.println("updating coordinator with " + groups.size() + " groups");
            coordinator.newGroups(groups.values());
            groups = new HashMap<Long, AffinityGroup>();
        }
    }

    /* --- Implement GroupFinder -- */

    @Override
    public synchronized void start() {
        System.out.println("starting user group finder task");
        groups = new HashMap<Long, AffinityGroup>();
        finderTask = taskScheduler.scheduleRecurringTask(
                                    new AbstractKernelRunnable("FinderTask") {
                                            public void run() {
                                                System.out.println("user group finder task run");
                                                updateCoordinator();
                                            }},
                                    taskOwner,
                                    System.currentTimeMillis(),
                                    30 * 1000);
        finderTask.start();
    }

    @Override
    public synchronized void stop() {
        System.out.println("stopping user group finder task");
        if (finderTask != null) {
            finderTask.cancel();
            finderTask = null;
        }
        groups = null;
    }

    @Override
    public void shutdown() {
        stop();
        exporter.unexport();
    }
}