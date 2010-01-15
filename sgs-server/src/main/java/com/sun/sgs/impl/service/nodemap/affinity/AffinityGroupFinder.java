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

/**
 *  The affinity group finder finds affinity groups within a
 *  Darkstar cluster using the LPA algorithm.
 */
public interface AffinityGroupFinder {

    /**
     * Finds affinity groups across all nodes in the Darkstar cluster.
     * If an error is encountered during a run, an
     * {@code AffinityGroupFinderFailedException}
     * is thrown. Errors include nodes not responding to server requests.
     *
     * @param groupSet the group set to populate
     * @return how long the find process took in milliseconds
     * @throws AffinityGroupFinderFailedException if there is an error
     * @throws IllegalStateException if the finder is disabled or shut down
     */
    long findAffinityGroups(GroupSet groupSet) throws AffinityGroupFinderFailedException;

    /** Enables the finder. */
    void enable();
    /** Disables the finder. */
    void disable();
    /** Shuts down the finder. */
    void shutdown();
}
