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

import com.sun.sgs.service.NoNodesAvailableException;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import java.util.Properties;

/**
 * Thing that manages groups of identities. The class that implements this
 * interface should be public, not abstract, and should provide a public
 * constructor with {@link Properties}, {@link NodeMappingServerImpl}, and
 * {@link DataService} parameters.
 */
public interface GroupCoordinator {

    /**
     * Stop the coordinator.
     */
    void start();

    /**
     * Start the coordinator.
     */
    void stop();

    /**
     * Move one or more identities off of the old node. If the old node is alive
     * a single group of identities will be selected to move. How the group
     * is selected is implementation dependent. If the node is not alive,
     * all groups will be moved. Note that is this does not guarantee that all
     * identities will be moved.
     *
     * @param oldNode the node to offload identities
     *
     * @throws NullPointerException if {@code oldNode} is {@code null}
     * @throws NoNodesAvailableException if no nodes are available
     */
    void offload(Node oldNode) throws NoNodesAvailableException;

    /**
     * Guess...
     */
    void shutdown();
}
