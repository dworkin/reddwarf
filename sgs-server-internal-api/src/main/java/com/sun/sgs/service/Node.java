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

package com.sun.sgs.service;

/**
 * An abstraction for node information, used in conjunction
 * with the {@link WatchdogService} and {@link NodeListener}s.
 */
public interface Node {

    /**
     * Returns the node ID, which is always non-negative.
     *
     * @return the node ID
     */
    long getId();

    /** 
     * Returns this node's host name.
     *
     * @return	this node's host name
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
}
