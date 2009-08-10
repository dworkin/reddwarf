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

import com.sun.sgs.auth.Identity;
import java.util.Set;

/**
 * An affinity group in the system.  Affinity groups are sets of
 * identities that have formed a community.
 * 
 */
public interface AffinityGroup {
    /**
     * Returns the affinity group identity.
     * @return the affinity group identity
     */
    long getId();

    /**
     * Returns the set of {@code Identities} which are members of this group.
     * @return the set of {@code Identities} which are members of this group
     */
    Set<Identity> getIdentities();
}
