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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.management;

import java.util.List;

/**
 * Management interface for group coordinator.
 */
public interface GroupCoordinatorMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs.core:type=GroupCoordinator";

    /**
     * Group information.
     */
    public interface GroupInfo {

        /**
         * Get the group ID of this group.
         *
         * @return a group ID
         */
        long getGroupId();

        /**
         * Get the target node for this group.
         *
         * @return a node ID
         */
        long getTargetNodeId();

        /**
         * Get the list of identities in this group.
         *
         * @return a list of identities
         */
        List<String> getIdentities();
    }

    /**
     * Return {@code true} if the coordinator is enabled.
     *
     * @return {@code true} if the coordinator is enabled
     */
    boolean isEnabled();

    /**
     * Enable the coordinator.
     */
    void enable();

    /**
     * Disable the coordinator.
     */
    void disable();

    /**
     * Get the total number of groups on all nodes.
     *
     * @return the number of groups
     */
    int getNumGroups();

    /**
     * Get the groups on the specified node.
     *
     * @param nodeId a node id
     *
     * @return a group information object
     */
    List<GroupInfo> getGroups(long nodeId);
}
