/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap.affinity.dgb;

import com.sun.sgs.auth.Identity;
import java.io.IOException;
import java.rmi.Remote;

/**
 *  The server interface for the distributed graph builder.
 */
public interface DistGraphBuilderServer extends Remote {
    /**
     * Update the graph based on the objects accessed in a task.
     *
     * @param owner  the task owner (the object making the accesses)
     * @param objIds the object IDs of objects accessed by the owner
     * @throws IOException if there is a communication problem
     */
    void updateGraph(Identity owner, Object[] objIds) throws IOException;
}
