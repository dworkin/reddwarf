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

package com.sun.sgs.impl.service.nodemap.coordinator.affinity.user;

import com.sun.sgs.impl.service.nodemap.coordinator.affinity.*;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server side implementation of component that finds groups based on hints
 * provided by the application.
 */
public class UserGroupFinderServerImpl implements AffinityGroupFinder,
                                                  UserGroupFinderServer
{
    /** Package name for this class. */
    private static final String PKG_NAME =
                        "com.sun.sgs.impl.service.nodemap.coordinator.affinity";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME + ".finder"));

    /** The property name for the update period. */
    static final String UPDATE_PERIOD_PROPERTY = PKG_NAME + ".server.port";

    /** The default value of the update period, in seconds. */
    static final int DEFAULT_UPDATE_PERIOD = 30;

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

    // Map nodeId -> source
    private final Map<Long, UserGroupSource> sources =
                                        new HashMap<Long, UserGroupSource>();

    private final long updatePeriod;
    private RecurringTaskHandle updateTask = null;

    private long generation = 0;

    public UserGroupFinderServerImpl(Properties properties,
                              AffinityGroupCoordinator coordinator,
                              ComponentRegistry systemRegistry,
                              TransactionProxy txnProxy)
        throws Exception
    {
        logger.log(Level.CONFIG, "Creating UserGroupFinderServerImpl");

        this.coordinator = coordinator;
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        int updateSeconds = wrappedProps.getIntProperty(UPDATE_PERIOD_PROPERTY,
                                               DEFAULT_UPDATE_PERIOD, 5, 65535);
        updatePeriod = updateSeconds * 1000;

        int requestedPort = wrappedProps.getIntProperty(SERVER_PORT_PROPERTY,
                                                        DEFAULT_SERVER_PORT,
                                                        0, 65535);

        logger.log(Level.CONFIG, "UserGroupFinderServerImpl, " +
                   "requested port: {0,number,#}, update period: {1} ms",
                   requestedPort, updatePeriod);

        // Export ourselves.  At this point, this object is public.
        exporter =
               new Exporter<UserGroupFinderServer>(UserGroupFinderServer.class);
        int port = exporter.export(this, SERVER_EXPORT_NAME, requestedPort);
        if (requestedPort == 0) {
            logger.log(Level.CONFIG, "Server is using port {0,number,#}", port);
        }
    }

    private boolean started() {
        return updateTask != null;
    }

    /* --- Implement UserGroupFinderServer -- */

    @Override
    public void registerUserGroupSource(UserGroupSource source, long nodeId)
        throws IOException
    {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "registering source from node {0}", nodeId);
        }

        sources.put(nodeId, source);
        if (started()) {
            source.start();
        }
    }

    /* --- Implement AffinityGroupFinder -- */

    @Override
    public synchronized void start() {
        if (started()) return; // already started

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "starting update task with period of {0} ms",
                       updatePeriod);
        }
        callSources(true);
        updateTask = taskScheduler.scheduleRecurringTask(
                                    new AbstractKernelRunnable("UpdateTask") {
                                            public void run() {
                                                updateCoordinator();
                                            }},
                                    taskOwner,
                                    System.currentTimeMillis() + updatePeriod,
                                    updatePeriod);
        updateTask.start();
    }

    @Override
    public synchronized void stop() {
        if (started()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "stopping finder");
            }
            if (updateTask != null) {
                updateTask.cancel();
                updateTask = null;
            }
            callSources(false);
        }
    }

    @Override
    public void shutdown() {
        stop();
        exporter.unexport();
    }
    
    /* --- private methods -- */
    
    private void callSources(boolean start) {
        Iterator<UserGroupSource> iter = sources.values().iterator();
        while (iter.hasNext()) {
            UserGroupSource source = iter.next();
            try {
                if (start) {
                    source.start();
                } else {
                    source.stop();
                }
            } catch (IOException ioe) {
                logger.logThrow(Level.WARNING, ioe,
                                "exception contacting source, removing it");
                iter.remove();
            }
        }
    }
        
    // Collect associations from the sources, assemble the groups, and update
    // the coordinator.
    private synchronized void updateCoordinator() {
        if (!started()) return;

        generation++;

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                       "starting update for generation " + generation);
        }
        
        // Map groupId -> map(Identity -> nodeId)
        final Map<Long, Map<Identity, Long>> groups =
                                    new HashMap<Long, Map<Identity, Long>>();
        
        Iterator<Map.Entry<Long, UserGroupSource>> iter =
                                                sources.entrySet().iterator();
        
        while (iter.hasNext()) {
            Map.Entry<Long, UserGroupSource> entry = iter.next();
            try {
                associations(groups,
                             entry.getValue().getAssociations(),
                             entry.getKey());
            } catch (IOException ioe) {
                logger.logThrow(Level.WARNING, ioe,
                                "exception contacting source, removing it");
                iter.remove();
            }
        }
        
        List<AffinityGroup> affinityGroups =
                                new ArrayList<AffinityGroup>(groups.size());

        for (Map.Entry<Long, Map<Identity, Long>> e : groups.entrySet()) {
            affinityGroups.add(coordinator.newInstance(e.getKey(), e.getValue(),
                                                       generation));
        }
        coordinator.newGroups(affinityGroups);
    }
    
    private void associations(Map<Long, Map<Identity, Long>> groups,
                              Map<Identity, Long> associations,
                              long nodeId)
    {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "received {0} associations from node {1}",
                       associations.size(), nodeId);
        }

        // Identity -> groupdId
        for (Map.Entry<Identity, Long> entry : associations.entrySet()) {
            Map<Identity, Long> group = groups.get(entry.getValue());

            if (group == null) {
                group = new HashMap<Identity, Long>();
                groups.put(entry.getValue(), group);
            }
            group.put(entry.getKey(), nodeId);
        }
    }
}