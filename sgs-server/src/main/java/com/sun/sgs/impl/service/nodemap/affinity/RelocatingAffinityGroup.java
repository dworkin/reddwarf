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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Stub, to be replaced by Keith's version (used for testing until we merge).
 * <p>
 * An affinity group which can relocate its member identities to other nodes.
 * Key ideas swiped from Keith's branch to help us with merging.
 * <p>
 * These affinity groups can span multiple nodes and will eventually
 * relocate their members to a single node.  There probably needs to be
 * some external control of the relocation in case we merge affinity groups
 * or find that an algorithm run returns a single group.
 */
public class RelocatingAffinityGroup implements AffinityGroup, Comparable {
    // The group id
    private final long agid;
    // Map Identity -> nodeId
    private final Map<Identity, Long> identities;
    // The node id that most Identities seem to be on
    private final long targetNodeId;
    // Generation number
    private final long generation;

    // hashcode, lazily computed
    private volatile int hashcode;
    /**
     * Creates a new affinity group containing node information.
     * @param agid the group id
     * @param identities the identities and their nodes in the group
     * @param generation the generation number of this group
     * @throws IllegalArgumentException if {@code identities} is empty
     */
    public RelocatingAffinityGroup(long agid,
                                   Map<Identity, Long> identities,
                                   long generation)
    {
        if (identities.size() == 0) {
            throw new IllegalArgumentException("Identities must not be empty");
        }
        this.agid = agid;
        this.identities = identities;
        this.generation = generation;
        targetNodeId = calcMostUsedNode();
    }

    /**
     * Calculate the node that the most number of identities is on.
     * @return the node id of the most used node
     */
    private long calcMostUsedNode() {
        Map<Long, Integer> nodeCountMap = new HashMap<Long, Integer>();
        for (Long nodeId : identities.values()) {
            if (nodeId == -1) {
                // Node id was unknown, so don't count it
                continue;
            }
            Integer count = nodeCountMap.get(nodeId);
            int val = (count == null) ? 0 : count.intValue();
            val++;
            nodeCountMap.put(nodeId, Integer.valueOf(val));
        }
        long retNode = -1;
        int highestCount = -1;
        for (Entry<Long, Integer> entry : nodeCountMap.entrySet()) {
            int count = entry.getValue();
            if (highestCount < count) {
                highestCount = count;
                retNode = entry.getKey();
            }         
        }
        return retNode;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sorts based on the number of identities in the groups.
     */

    @Override
    public int compareTo(Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        if (this.equals(obj)) {
            return 0;
        }

        int mySize = identities.size();
        int otherSize = ((RelocatingAffinityGroup) obj).identities.size();
        if (mySize == otherSize) {
            return 0;
        } else if (mySize < otherSize) {
            return -1;
        } else {
            return 1;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RelocatingAffinityGroup)) {
            return false;
        }
        RelocatingAffinityGroup other = (RelocatingAffinityGroup) obj;
        return (agid == other.agid) &&
               (generation == other.generation) &&
               (identities.equals(other.identities));
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        if (hashcode == 0) {
            int result = 17;
            result = result * 37 + (int) (agid  ^ agid >>> 32);
            result = result * 37 + (int) (generation ^ generation >>> 32);
            result = result * 37 + identities.hashCode();
            hashcode = result;
        }
        return hashcode;
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

    /** {@inheritDoc} */
    public long getGeneration() {
        return generation;
    }
}
