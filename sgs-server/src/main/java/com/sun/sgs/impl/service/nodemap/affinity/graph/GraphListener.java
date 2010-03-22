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

package com.sun.sgs.impl.service.nodemap.affinity.graph;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.SystemIdentity;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.util.Properties;

/**
 * A listener which detects object uses by Identities within tasks and builds
 * a graph based on that information.
 */
public class GraphListener implements ProfileListener {
    /** The affinity graph builder. */
    private final AffinityGraphBuilder builder;

    /**
     * Constructs a new listener for affinity graph data.
     * <p>
     * NOTE: this constructor should never be used, but is provided to
     * satisfy the {@code ProfileListener} documentation.  This listener
     * cannot be provided as as a property, it is created within the system
     * for the server's use.
     *
     * @param properties the configuration properties
     * @param owner task owner for any tasks run by this listener
     * @param registry the system registry
     */
    public GraphListener(Properties properties,
                         Identity owner,
                         ComponentRegistry registry) {
        throw new NullPointerException("null builder not allowed");
    }
    /**
     * Constructs a new listener for affinity graph data.
     *
     * @param builder the affinity graph builder to report to
     * 
     * @throws NullPointerException if the builder is {@code null}
     */
    public GraphListener(AffinityGraphBuilder builder)
    {
        if (builder == null) {
            throw new NullPointerException("null builder not allowed");
        }
        this.builder = builder;
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
        // We don't care about accesses by the system identity, since
        // these identities cannot move to other nodes. The affinity graphs
        // consist of application information only to help reduce their
        // size.  The system identity is pinned to a node.
        if (owner instanceof SystemIdentity) {
            return;
        }
        
        AccessedObjectsDetail detail = profileReport.getAccessedObjectsDetail();
        if (detail == null) {
            return;
        }

        builder.updateGraph(owner, detail);
    }
}
