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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;

/**
 * An affinity group is a set of Identities which should be collocated on a
 * single node, if possible.
 */
public interface AffinityGroup {

    /**
     * Returns the affinity group ID.
     *
     * @return the affinity group ID
     */
    long getId();

    /**
     * Returns the set of {@link Identity}s which are members of this affinity
     * group.
     *
     * @return the set of identities which are members of this affinity group
     */
    Identity[] getIdentities();

    /**
     * Set the node that the identities of this group should be located on.
     *
     * @param nodeId
     */
    void setNode(long nodeId);
}
