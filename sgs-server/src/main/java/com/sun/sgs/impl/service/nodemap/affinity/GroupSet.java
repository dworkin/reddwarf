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
import java.util.Map;
import java.util.Set;

/**
 * Container for affinity groups.
 */
public interface GroupSet {

    /**
     * Add an affinity group to this set.
     *
     * @param groupId the group ID
     * @param members the set of members
     * @param generation the generation
     */
    void add(long groupId, Map<Identity, Long> members, long generation);

    /**
     * Return the number of member groups.
     * @return the number of member groups
     */
    int size();

    /**
     * Return {@code true} if the member set is empty, otherwise {@code false}.
     * @return {@code true} if the member set is empty, otherwise {@code false}
     */
    boolean isEmpty();

    /**
     * Get the set of member groups.
     * @return the set of member groups
     */
    Set<AffinityGroup> getGroups();
}
