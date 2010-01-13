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

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.GraphListener;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A driver for the Label Propagation Algorithm affinity group finders.
 * The driver is responsible for instantiating the affinity group system,
 * managing its life cycle, and invoking the group finder as necessary.
 * <p>
 * A driver must be instantiated on each node of the Darkstar cluster.
 * <p>
 * The following properties are supported:
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt>	<i>Property:</i> <code><b>
 *   com.sun.sgs.impl.service.nodemap.affinity.graphbuilder.class
 *	</b></code><br>
 *	<i>Default:</i>
 *    {@code
 *    com.sun.sgs.impl.service.nodemap.affinity.graph.dlpa.WeightedGraphBuilder}
 * <br>
 *
 * <dd style="padding-top: .5em">The graph builder to use.  Set to
 *   {@code None} if no affinity group finding is required, which is
 *   useful for testing. <p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *   com.sun.sgs.impl.service.nodemap.affinity.update.period
 *	</b></code><br>
 *	<i>Default:</i> {@code 60} (one minute)}
 * <br>
 *
 * <dd style="padding-top: .5em">The frequency that we find affinity groups,
 *  in seconds.  The value must be between {@code 5} and {@code 65535}.<p>
 * </dl>
 */
public class LPADriver extends BasicState implements AffinityGroupFinder {
    /** The base name for properties. */
    private static final String PROP_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /** The property for specifying the graph builder class. */
    public static final String GRAPH_CLASS_PROPERTY =
        PROP_NAME + ".graphbuilder.class";

    /** The property name for the update frequency. */
    public static final String UPDATE_FREQ_PROPERTY =
        PROP_NAME + ".update.freq";

    /** The default value of the update frequency, in seconds. */
    static final int DEFAULT_UPDATE_FREQ = 60;

    /**
     * The value to be given to {@code GRAPH_CLASS_PROPERTY} if no
     * affinity group finding should be instantiated (useful for testing).
     */
    public static final String GRAPH_CLASS_NONE = "None";

    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PROP_NAME));

    /** A graph listener, used for detecting affinity groups, or {@code null}
     *  if no graph listener is being used.
     */
    private final GraphListener graphListener;
    
    /** The affinity graph builder, null if there is none. */
    private final AffinityGraphBuilder graphBuilder;

    private final TaskScheduler taskScheduler;
    private final Identity taskOwner;
    private final long updatePeriod;
    private RecurringTaskHandle updateTask = null;

    /**
     * Constructs an instance of this class with the specified properties.
     * <p>
     * @param	properties the properties for configuring this subsystem
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     *
     * @throws Exception if an error occurs during creation
     */
    public LPADriver(Properties properties,
                     ComponentRegistry systemRegistry,
                     TransactionProxy txnProxy)
        throws Exception
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        int updateSeconds = wrappedProps.getIntProperty(UPDATE_FREQ_PROPERTY,
                                               DEFAULT_UPDATE_FREQ, 5, 65535);
        updatePeriod = updateSeconds * 1000L;
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();

        NodeType type =
            NodeType.valueOf(
                properties.getProperty(StandardProperties.NODE_TYPE));
        String builderName = wrappedProps.getProperty(GRAPH_CLASS_PROPERTY);
        if (GRAPH_CLASS_NONE.equals(builderName)) {
            // do not instantiate anything
            graphBuilder = null;
            graphListener = null;
            return;
        }
        if (builderName != null) {
            graphBuilder = wrappedProps.getClassInstanceProperty(
                    GRAPH_CLASS_PROPERTY, AffinityGraphBuilder.class,
                    new Class[] { Properties.class,
                                  ComponentRegistry.class,
                                  TransactionProxy.class },
                    properties, systemRegistry, txnProxy);
        // TODO: The following code is commented out while our default is none,
        // mostly to keep findbugs quiet.  In the future, we expect the
        // WeightedGraphBuilder to be the default if we're not in single node
        // mode.
//        } else if (type != NodeType.singleNode) {
//            // Default is currently NONE, might become the distributed LPA/
//            // weighted graph listener in the future.
//            builder = null;
        } else {
            // Either we're in multi-node, but the current default is
            // no action, or we're in single node and no builder was requested.
            //
            // If we're in single node, and no builder was requested,
            // don't bother creating anything.  Affinity groups will make
            // no sense.
            graphBuilder = null;
        }

        // Add the self as listener if there is a builder and we are
        // not a core server node.
        if (graphBuilder != null && type != NodeType.coreServerNode) {
            ProfileCollector col =
                systemRegistry.getComponent(ProfileCollector.class);
            graphListener = new GraphListener(graphBuilder);
            col.addListener(graphListener, false);
        } else {
            graphListener = null;
        }
        logger.log(Level.CONFIG,
                       "Created LPADriver with listener: " + graphListener +
                       ", builder: " + graphBuilder + ", and properties:" +
                       "\n  " + GRAPH_CLASS_PROPERTY + "=" + builderName +
                       "\n  " + UPDATE_FREQ_PROPERTY + "=" + updateSeconds);
    }

    /** {@inheritDoc} */
    @Override
    public void disable() {
        if (setDisabledState()) {
            logger.log(Level.FINE, "LPA driver disabled");
            if (graphBuilder != null) {
                graphBuilder.disable();
            }
            if (updateTask != null) {
                updateTask.cancel();
                updateTask = null;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void enable() {
        if (setEnabledState()) {
            logger.log(Level.FINE, "LPA driver enabled");
            if (graphBuilder != null) {
                graphBuilder.enable();
                if (graphBuilder.getAffinityGroupFinder() != null) {
                    updateTask = taskScheduler.scheduleRecurringTask(
                                    new AbstractKernelRunnable("UpdateTask") {
                                            public void run() {
                                                findGroups();
                                            } },
                                    taskOwner,
                                    System.currentTimeMillis() + updatePeriod,
                                    updatePeriod);
                    updateTask.start();
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
        if (setShutdownState()) {
            logger.log(Level.FINE, "LPA driver shut down");
            if (updateTask != null) {
                updateTask.cancel();
            }
            if (graphListener != null) {
                graphListener.shutdown();
            }
            if (graphBuilder != null) {
                graphBuilder.shutdown();
            }
        }
    }

    /**
     * Returns the graph builder used by this driver, or {@code null} if
     * we are not building graphs.
     * @return the graph builder or {@code null} if there is none
     */
    public AffinityGraphBuilder getGraphBuilder() {
        return graphBuilder;
    }

    /**
     * Returns the graph listener used by this driver, or {@code null} if
     * we are not building graphs.
     * @return the graph listener or {@code null} if there is none
     */
    public GraphListener getGraphListener() {
        return graphListener;
    }

    /**
     * Try to gather a new set of groups, pushing the results if successful.
     */
    private void findGroups() {
        try {
            // TODO:  Dead store until integrated with coordinator
            // Set<AffinityGroup> groups =
                    graphBuilder.getAffinityGroupFinder().findAffinityGroups();
            // tell the coordinator about the found groups
        } catch (AffinityGroupFinderFailedException e) {
            logger.logThrow(Level.INFO, e, "Affinity group finder failed");
        }
    }
}
