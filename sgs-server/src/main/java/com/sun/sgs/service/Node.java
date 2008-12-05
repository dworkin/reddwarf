/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.service;

import com.sun.sgs.protocol.session.ProtocolDescriptor;

/**
 * An abstraction for node information, used in conjunction
 * with the {@link WatchdogService} and {@link NodeListener}s.
 */
public interface Node {

    /**
     * Returns the node ID.
     *
     * @return the node ID
     */
    long getId();

    /** 
     * Returns this node's hostname.
     *
     * @return	this node's hostname
     */
    String getHostName();
    
    /**
     * Returns {@code true} if the node is known to be alive, and
     * {@code false} if the node is thought to have failed or is
     * unknown.
     *
     * @return	{@code true} if the node is alive, and {@code false}
     * 		otherwise
     */
    boolean isAlive();
    
    /**
     * Returns the set of transports descriptors that respresent the
     * transports listening for client connections on this
     * node. {@code null} is returned if this is not an application node.
     * 
     * @return the set of transport descriptors or {@code null}
     */
    ProtocolDescriptor[] getClientListeners();
    
    /**
     * Sets the set of transports listening for client connections on this node.
     * The specified set will override the value from a previous call.
     * 
     * @param descriptors set of transport descriptors
     */
    void setClientListener(ProtocolDescriptor[] descriptors);

}
