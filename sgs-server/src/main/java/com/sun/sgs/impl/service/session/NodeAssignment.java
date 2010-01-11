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

package com.sun.sgs.impl.service.session;

/**
 * Contains a node ID assignment.
 *
 * TBD: add a service-level interface for ClientSession?
 */
public interface NodeAssignment {

    /**
     * Returns the node ID for this instance.
     *
     * @return	the node ID for this instance
     */
    long getNodeId();

    /**
     * Returns the ID of the new node that the client session is
     * relocating to, or {@code -1} if the associated client session
     * is not relocating.
     *
     * @return	the node ID of the new node, or {@code -1} if the
     *		session is not relocating
     */
    long getRelocatingToNodeId();
}
