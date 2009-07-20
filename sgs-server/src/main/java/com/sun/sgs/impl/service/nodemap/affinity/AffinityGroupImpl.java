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
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 *  This will need to change, of couse, for multinode.
 */
public class AffinityGroupImpl implements AffinityGroup, Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    private final long id;
    private final HashSet<Identity> identities = new HashSet<Identity>();

    /**
     * Constructs a new affinity group with the given identity.
     * @param id the affinity group identity
     */
    public AffinityGroupImpl(long id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    public long getId() {
        return id;
    }

    /** {@inheritDoc} */
    public synchronized Set<Identity> getIdentities() {
        return Collections.unmodifiableSet(identities);
    }

    /** {@inheritDoc} */
    public String toString() {
        return getClass().getName() + "[" + id +
               ",  size: " + identities.size() + "]";
    }

    /**
     * Add the given identity to this affinity group.
     * @param id the identity to add
     */
    public synchronized void addIdentity(Identity id) {
        identities.add(id);
    }
}
