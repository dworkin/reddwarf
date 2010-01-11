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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.service.Node;
import java.io.IOException;
import java.rmi.Remote;

/**
 *  Callback for clients of the server to update them with information
 *  about identity membership changes to their node.
 */
interface NotifyClient extends Remote {
    
    /**
     * An identity has been assigned to this node.
     *
     * @param id the identity
     * @param oldNode the last node the identity was assigned to, or
     *           {@code null} if this is a new node assignment
     * @throws IOException if there is a communication problem
     */
    void added(Identity id, Node oldNode) throws IOException;
    
    /**
     *
     * An identity has been removed from this node.
     * 
     * @param id the identity
     * @param newNode the new node the identity is assigned to, or 
     *          {@code null} if the identity is being removed from
     *          the map
     * @throws IOException if there is a communication problem 
     */
    void removed(Identity id, Node newNode) throws IOException;
    
    /**
     * An identity has been selected for relocation from this node.
     * 
     * @param id the identity
     * @param newNodeId the ID of the new node the identity will be relocated to
     * @throws IOException if there is a communication problem 
     */
    void prepareRelocate(Identity id, long newNodeId) throws IOException;
}
