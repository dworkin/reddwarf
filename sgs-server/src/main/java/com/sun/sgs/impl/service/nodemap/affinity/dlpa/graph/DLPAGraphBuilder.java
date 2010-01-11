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

package com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import java.util.Map;

/**
 * Graph builder interface for use with the distributed label propagation
 * algorithm implementation.  It includes necessary additional information
 * for that algorithm (object use information and cache conflicts, used to
 * find graph links to other nodes), as well as a way to remove failed nodes.
 */
public interface DLPAGraphBuilder extends AffinityGraphBuilder {
    /**
     * Returns a map of local object uses to the identities that used
     * the objects, and a count of the number of uses. An empty map will
     * be returned if there are no object uses.
     *
     * @return the map of local object uses
     */
    Map<Object, Map<Identity, Long>> getObjectUseMap();

    /**
     * Returns a map of detected cross node data conflicts.  Conflicts
     * occur when an object is in use by the current node but is needed by
     * another node. This is a map of node IDs (the nodes requesting the
     * object) to object IDs, and a count of the number of conflicts on the
     * object with that node.  An empty map will be returned if there are no
     * conflicts.  If more than one node needs an object at about the same
     * time, it is only required that one node be recorded as a conflict.
     * 
     * @return the map of detected cross node data conflicts
     */
    Map<Long, Map<Object, Long>> getConflictMap();

    /**
     * Note that a node has failed.  Does nothing if the {@code nodeId} is
     * unknown or has already been noted as failed.
     * 
     * @param nodeId the id of the failed node
     */
    void removeNode(long nodeId);
}
