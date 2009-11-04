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

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;

/**
 * A group coordinator manages groups of identities. How identities are grouped
 * is implementation dependent. Since grouping activities may generate load on
 * the system, the coordinator may be disabled if conditions merit.<p>
 * 
 * The class that implements this interface must be public, not abstract, and
 * should provide a public constructor with {@link Properties},
 * {@link NodeMappingServerImpl}, {@link ComponentRegistry}, and
 * {@link TransactionProxy} parameters.<p>
 *
 * A newly constructed coordinator should be in the disabled state.
 */
public interface GroupCoordinator {

    /**
     * Enable coordination. If the coordinator is enabled, calling this method
     * will have no effect.
     */
    void enable();

    /**
     * Disable coordination. If the coordinator is disabled, calling this method
     * will have no effect.
     */
    void disable();

    /**
     * Move one or more identities off of a node. If the old node is alive
     * a group of identities will be selected to move. How the group
     * is selected is implementation dependent. If the node is not alive,
     * all identities on that node will be moved. Note that is this method does
     * not guarantee that any identities are be moved.
     *
     * @param node the node to offload identities
     *
     * @throws NullPointerException if {@code oldNode} is {@code null}
     * @throws NoNodesAvailableException if no nodes are available
     */
    void offload(Node node) throws NoNodesAvailableException;

    /**
     * Shutdown the coordinator. The coordinator is disabled and
     * all resources released. Any further method calls made on the coordinator
     * will result in a {@code IllegalStateException} being thrown.
     */
    void shutdown();
}
