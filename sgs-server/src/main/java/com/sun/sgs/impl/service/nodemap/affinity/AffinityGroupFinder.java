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

import java.util.Set;

/**
 *  The affinity group finder finds affinity groups within a
 *  Darkstar cluster.
 */
public interface AffinityGroupFinder {

    /**
     * Finds affinity groups across all nodes in the Darkstar cluster.
     * If an error is encountered during a run, an
     * {@code AffinityGroupFinderFailedException}
     * is thrown. Errors include nodes not responding to server requests.
     * If an {@code AffinityGroupFinderFailedException} exception is thrown,
     * it is transient and applies only to the current method call.
     * <p>
     * This might be a long running call, as remote method calls might be made.
     *
     * @param <T> type of affinity group
     * @param groupSet the group set to populate
     * @param factory the factory for creating elements to be placed in groupSet
     * @return how long the find process took in milliseconds
     * @throws AffinityGroupFinderFailedException if there is an error
     * @throws IllegalStateException if the finder is disabled or shut down
     */
    <T extends AffinityGroup> long findAffinityGroups(Set<T> groupSet,
                                                      AffinityGroupFactory<T> factory)
        throws AffinityGroupFinderFailedException;

    /** Enables the finder. */
    void enable();
    /** Disables the finder. */
    void disable();
    /** Shuts down the finder. */
    void shutdown();
}
