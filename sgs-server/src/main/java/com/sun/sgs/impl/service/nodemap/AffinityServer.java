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
 * Interface which must be implemented by components creating affinity groups.
 */
public interface AffinityServer {

    /**
     * Returns the affinity group the {@link Identity} belongs to. If the
     * identity is not known to belong to any group, {@code null} is returned.
     *
     * @param id an identity
     *
     * @return an affinity group or {@code null}
     */
    AffinityGroup getGroup(Identity id);

    /**
     * Returns the affinity group with the given affinity group ID. If there
     * is not group associated with the ID {@code null} is returned.
     *
     * @param agid an affinity group ID
     *
     * @return an affinity group or {@code null}
     */
    AffinityGroup getGroup(long agid);
}
