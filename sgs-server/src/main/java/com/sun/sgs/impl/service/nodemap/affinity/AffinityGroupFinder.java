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

import java.util.Collection;

/**
 *  The affinity group finder finds affinity groups within a
 *  Darkstar cluster.
 */
public interface AffinityGroupFinder {
    /**
     * Finds affinity groups across all nodes in the Darkstar cluster.
     * If an error is encountered during a run, an empty collection is
     * returned.  Errors include nodes not responding to server requests.
     *
     * @return the affinity groups
     */
    Collection<AffinityGroup> findAffinityGroups();

    /**
     * Removes any cached information about a failed node.
     * @param nodeId the id of a failed node
     */
    void removeNode(long nodeId);

    /**
     * Shuts down the affinity group finder.
     */
    void shutdown();
}
