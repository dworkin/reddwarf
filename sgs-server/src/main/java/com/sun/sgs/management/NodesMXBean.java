/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.management;

/**
 *  A management interface to find the nodes in the system.
 */
public interface NodesMXBean {
    /** The name for uniquely identifying this MBean. */
    String NODES_MXBEAN_NAME = "com.sun.sgs:type=Nodes";
    
    /** 
     * Information about the nodes in the system.
     * @return information about the nodes in the system
     */
    NodeInfo[] getNodes();
}
