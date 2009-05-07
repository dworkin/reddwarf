/*
 * Copyright 2009 Sun Microsystems, Inc.
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
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import edu.uci.ics.jung.graph.Graph;
import java.beans.PropertyChangeEvent;
import java.util.Properties;

/**
 * A listener which detects object uses by Identities within tasks.
 */
public class GraphListener implements ProfileListener {
    // the affinity graph builder used by the system
    private final GraphBuilder builder;

    /**
     * Constructs a new listener instance.  This listener is constructed
     * by the kernel, and always used.
     * 
     * @param properties application properties
     */
    public GraphListener(Properties properties) {
        builder = new GraphBuilder(properties);
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
        
        // TODO want to make this call in a separate thread, so 
        // we don't hold up other listeners?
        builder.buildGraph(owner, detail);
    }
    
    /**
     * Returns the current graph, with identities as vertices, and 
     * weighted edges representing each object accessed by both identity
     * endpoints.
     * 
     * @return the folded graph of accesses
     */
    public Graph<Identity, AffinityEdge> getAffinityGraph() {
        return builder.getAffinityGraph();
    }
}
