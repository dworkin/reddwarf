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

import java.util.NavigableSet;

/**
 *  The affinity group finder finds affinity groups within a
 *  Darkstar cluster using the LPA algorithm.
 */
public interface LPAAffinityGroupFinder extends AffinityGroupFinder {
    /**
     * Finds affinity groups across all nodes in the Darkstar cluster.
     * If no groups are found, an empty set is returned. If an error is
     * encountered during a run, an {@code AffinityGroupFinderFailedException}
     * is thrown. Errors include nodes not responding to server requests.
     * 
     * @throws AffinityGroupFinderFailedException if there is an error
     * @throws IllegalStateException if the finder is disabled or shut down
     * @return the affinity groups, or an empty set if none are found
     */
    NavigableSet<RelocatingAffinityGroup> findAffinityGroups()
            throws AffinityGroupFinderFailedException;
}
