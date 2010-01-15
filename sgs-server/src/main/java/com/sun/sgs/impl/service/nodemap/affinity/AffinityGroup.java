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
import java.util.Set;

/**
 * An affinity group in the system.  Affinity groups are sets of
 * identities that have formed a community.
 * <p>
 * Affinity groups have an identifier and a generation number. Affinity groups
 * with different generation numbers cannot be compared.  In particular, two
 * groups which have the same affinity group identifier but different
 * generation numbers cannot be assumed to be related in any way.
 */
public interface AffinityGroup {
    /**
     * Returns the affinity group identifier.
     * @return the affinity group identifier
     */
    long getId();

    /**
     * Returns the set of {@code Identities} which are members of this group.
     * The set will contain at least one member.
     * @return the set of {@code Identities} which are members of this group
     */
    Set<Identity> getIdentities();

    /**
     * Returns a generation number for this affinity group. Affinity groups
     * in the same generation were created at approximately the same time, so
     * they can be used as the set of communities found at that time.
     * Affinity groups with the same identifier but different generations
     * cannot be compared;  they are independent.
     * @return the generation number for this affinity group
     */
    long getGeneration();
}
