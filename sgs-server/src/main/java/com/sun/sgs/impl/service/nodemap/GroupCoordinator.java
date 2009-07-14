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

import com.sun.sgs.service.Node;

/**
 * Thing that manages groups of identities. The class that implements this
 * interface should be public, not abstract, and should provide a public
 * constructor with {@link Properties}, {@link NodeMappingServerImpl}, and
 * {@link DataService} parameters.
 */
public interface GroupCoordinator {

    /**
     * Move one or more identities from the old node to the new node. This
     * method will select a single group of identities to move. How the group
     * is selected is implementation dependent.
     *
     * @param oldNode the node to offload identities
     * @param newNodeId the id of the node to move identities to
     *
     * @throws NullPointerException if {@code oldNode} is {@code null}
     * @throws IllegalArgumentException if {@code newNodeId} is <= 0
     */
    void offload(Node oldNode, long newNodeId);

    /**
     * Guess...
     */
    void shutdown();
}
