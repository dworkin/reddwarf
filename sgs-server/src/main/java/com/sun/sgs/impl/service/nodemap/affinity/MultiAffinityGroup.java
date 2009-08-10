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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Affinity group.  Key ideas swiped from Keith's branch to help us with
 * merging.
 * <p>
 * These affinity groups can span multiple nodes.
 */
public class MultiAffinityGroup implements AffinityGroup {

    private final long agid;

    // Map Identity -> nodeId
    private final Map<Identity, Long> identities;

    private long targetNodeId = -1;

    /**
     * Creates a new affinity group which contains node information
     * @param agid the group id
     * @param identities the identities and their nodes in the group
     */
    MultiAffinityGroup(long agid, Map<Identity, Long> identities) {
        assert identities.size() > 0;
        this.agid = agid;
        this.identities = identities;
        System.out.println("group: " + agid);
        for (Map.Entry<Identity, Long> e : identities.entrySet()) {
            System.out.println("Identity: " + e.getKey().getName() +
                               " on node " + e.getValue());
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "AffinityGroup: " + agid + " targetNodeId: " + targetNodeId +
               " #identities: " + identities.size();
    }

    /** {@inheritDoc} */
    public long getId() {
        return agid;
    }

    /** {@inheritDoc} */
    public Set<Identity> getIdentities() {
        return Collections.unmodifiableSet(identities.keySet());
    }

}
