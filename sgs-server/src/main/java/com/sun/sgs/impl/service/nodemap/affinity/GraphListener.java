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

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.SystemIdentity;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import edu.uci.ics.jung.graph.Graph;
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
 *   com.sun.sgs.impl.service.nodemap.affinity.GraphListener.graphbuilder.class
 *	</b></code><br>
 *	<i>Default:</i>
 *   {@code com.sun.sgs.impl.service.nodemap.affinity.WeightedGraphBuilder} <br>
 *
 * <dd style="padding-top: .5em">The graph builder to use.<p>
 * </dl>
 */
public class GraphListener implements ProfileListener {
    // the base name for properties
    private static final String PROP_BASE = GraphListener.class.getName();
    
    /**
     * The public property for specifying the graph builder class.
     */
    public static final String GRAPH_CLASS_PROPERTY =
	PROP_BASE + ".graphbuilder.class";
    
    // the affinity graph builder
    private final GraphBuilder builder;

    /**
     * Constructs a new listener instance.  This listener is constructed
     * and registered by the kernel.
     * 
     * @param properties application properties
     */
    public GraphListener(Properties properties) {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        String builderClass = wrappedProps.getProperty(GRAPH_CLASS_PROPERTY);
        if (builderClass != null) {
            builder = wrappedProps.getClassInstanceProperty(
                        GRAPH_CLASS_PROPERTY, GraphBuilder.class,
                        new Class[] { Properties.class },
                        properties);
        } else {
            builder = new WeightedGraphBuilder(properties);
        }
    }
    
    /** {@inheritDoc} */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }

    /** {@inheritDoc} */
    public void shutdown() {
        // do nothing
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
     * Returns the current graph, with identities as vertices, and 
     * edges representing each object accessed by both identity
     * endpoints.
     * 
     * @return the folded graph of accesses
     */
    public Graph<Identity, ? extends WeightedEdge> getAffinityGraph() {
        return builder.getAffinityGraph();
    }

    /**
     * Returns the graph builder used by this listener.
     * @return the graph builder
     */
    public GraphBuilder getGraphBuilder() {
        return builder;
    }
}
