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

package com.sun.sgs.impl.service.nodemap.affinity.graph;

import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.GraphBuilder;
import
     com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.WeightedGraphBuilder;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.kernel.SystemIdentity;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.util.Properties;

/**
 * A listener which detects object uses by Identities within tasks and builds
 * a graph based on that information.
 * <p>
 * The following property is supported:
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
 * </dl>
 */
public class GraphListener implements ProfileListener {
    // the base name for properties
    private static final String PROP_BASE =
            "com.sun.sgs.impl.service.nodemap.affinity";
    /**
     * The public property for specifying the graph builder class.
     */
    public static final String GRAPH_CLASS_PROPERTY =
        PROP_BASE + ".graphbuilder.class";
    
    /**
     * The value to be given to {@code GRAPH_CLASS_PROPERTY} if no
     * affinity group finding should be instantiated (useful for testing).
     */
    public static final String GRAPH_CLASS_NONE = "None";

    // the affinity graph builder, null if there is none
    private final GraphBuilder builder;

    /**
     * Constructs a new listener instance. 
     *
     * @param col the profile collector
     * @param properties application properties
     * @param nodeId the local node id
     * @throws Exception if an error occurs
     */
    public GraphListener(ProfileCollector col, Properties properties, 
                         long nodeId)
        throws Exception
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        NodeType type =
            NodeType.valueOf(
                properties.getProperty(StandardProperties.NODE_TYPE));
        String builderClass = wrappedProps.getProperty(GRAPH_CLASS_PROPERTY);
        if (GRAPH_CLASS_NONE.equals(builderClass)) {
            // do not instantiate anything
            builder = null;
            return;
        }
        if (builderClass != null) {
            builder = wrappedProps.getClassInstanceProperty(
                GRAPH_CLASS_PROPERTY, GraphBuilder.class,
                new Class[] {ProfileCollector.class,
                             Properties.class, long.class},
                col, properties, nodeId);
        } else if (type != NodeType.singleNode) {
            builder = new WeightedGraphBuilder(col, properties, nodeId);
        } else {
            // If we're in single node, and no builder was requested,
            // don't bother creating anything.  Affinity groups will make
            // no sense.
            builder = null;
        }

        // Add the self as listener if we are an app node

        if (type == NodeType.appNode) {
            col.addListener(this, false);
        }
    }
    
    /** {@inheritDoc} */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }

    /** {@inheritDoc} */
    public void shutdown() {
        if (builder != null) {
            builder.shutdown();
        }
    }
    
    /** {@inheritDoc} */
    public void report(ProfileReport profileReport) {
        Identity owner = profileReport.getTaskOwner(); 
        // We don't care about accesses by the system identity
        if (owner instanceof SystemIdentity) {
            return;
        }
        
        AccessedObjectsDetail detail = profileReport.getAccessedObjectsDetail();
        if (detail == null) {
            return;
        }

        builder.updateGraph(owner, detail);
    }

    /**
     * Returns the graph builder used by this listener.
     * @return the graph builder
     */
    public GraphBuilder getGraphBuilder() {
        return builder;
    }
}
