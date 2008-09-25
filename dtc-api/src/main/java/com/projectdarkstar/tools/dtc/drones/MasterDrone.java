/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of the Darkstar Test Cluster
 *
 * Darkstar Test Cluster is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Darkstar Test Cluster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.projectdarkstar.tools.dtc.drones;

/**
 * The master drone serves two functions.
 * First, it monitors the test queue in persistent storage and initiates
 * tests by calling the appropriate test runners.  Second, it serves as a
 * result collector by receiving messages from slaves (which represent
 * log files and other result information) and forwarding the information
 * to the appropriate test runners controlling the tests for those slaves.
 */
public interface MasterDrone 
{

}
