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

/**
 * A factory for creating @{link AffinityGroup} instances.
 *
 * @param <E> the type of groups created by this factory
 */
public interface AffinityGroupFactory<E extends AffinityGroup> {

    /**
     * Create a instance of an affinity group.
     *
     * @param groupId the group ID
     * @param generation the generation number
     * @param members the set of member identities
     * 
     * @return a new affinity group
     */
    E newInstance(long groupId, long generation, Map<Identity, Long> members);
}
