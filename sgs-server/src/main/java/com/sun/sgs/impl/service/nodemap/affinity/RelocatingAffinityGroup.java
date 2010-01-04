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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An affinity group which includes node information to aid in collocating its
 * member Identities, and in moving Identities as a group.
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

    // The target node id that the Identities should be on. Initially set to
    // the node that most Identites seem to be on. A -1 indicates that target
    // node is not known and has not yet been set.
    private long targetNodeId;

    // List of identities not on the target node, or null
    private Set<Identity> stragglers;

    // Generation number
    private final long generation;

    // The weight of this group. Currently this is simply the size of the
    // group.
    private final int weight;

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
        setTargetNode();    // will also init stragglers
        weight = identities.size(); // if this changes, fix equals() etc...
    }

    /**
     * Set the target node id for this group to the node that the most number of
     * identities is on. If none of the nodes are known, the target node id is
     * set to -1.
     */
    private void setTargetNode() {
        long retNode = -1;
        int highestCount = -1;
        final Map<Long, Integer> nodeCountMap = new HashMap<Long, Integer>();
        for (Long nodeId : identities.values()) {
            Integer count = nodeCountMap.get(nodeId);
            int val = (count == null) ? 0 : count.intValue();
            val++;
            nodeCountMap.put(nodeId, Integer.valueOf(val));
            // If node id was unknown, don't use it as a target
            // (unless they all are)
            if ((highestCount < val) && (nodeId != -1)) {
                highestCount = val;
                retNode = nodeId;
            }
        }
        targetNodeId = retNode;

        // Optimization if we know everyone is on the same node, otherwise
        // set to null which will postpone the calculation until asked for
        if ((targetNodeId != -1) && nodeCountMap.isEmpty()) {
            stragglers = Collections.emptySet();
        } else {
            stragglers = null;
        }
    }

    /**
     * Set the target node id for this group.
     *
     * @param nodeId a node id
     */
    public synchronized void setTargetNode(long nodeId) {
        targetNodeId = nodeId;
        stragglers = null;  // will force recalc of list
    }

    /**
     * Get the target node id for this group.
     *
     * @return the target node if known, otherwise -1
     */
    public synchronized long getTargetNode() {
        return targetNodeId;
    }

    /**
     * Return the set of Identities that are not on the target node.
     *
     * @return the set of Identities that are not on the target node
     */
    public synchronized Set<Identity> getStragglers() {
        if (stragglers == null) {
            // If target node is unknown, then everyone is lost
            if (targetNodeId < 0) {
                stragglers = getIdentities();
            } else {
                final Set<Identity> ids = new HashSet<Identity>();
                for (Map.Entry<Identity, Long> entry : identities.entrySet()) {
                    if (entry.getValue() != targetNodeId) {
                        ids.add(entry.getKey());
                        entry.setValue(targetNodeId);
                    }
                }
                stragglers = Collections.unmodifiableSet(ids);
            }
        }
        return stragglers;
    }

    /* --- Implement AffinityGroup --- */

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

    /* --- Implement Comparable --- */

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

        int otherWeight = ((RelocatingAffinityGroup) obj).weight;
        if (weight == otherWeight) {
            return 0;
        } else if (weight < otherWeight) {
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
}
