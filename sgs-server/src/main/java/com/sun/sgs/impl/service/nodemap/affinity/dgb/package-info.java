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

/**
 * Provides classes for a multi-node distributed graph builder implementation
 * of the label propagation algorithm described in "Near linear time algorithm
 * to detect community structures in large-scale networks" by Raghavan, Albert
 * and Kumara (2007).
 * <p>
 * In this implementation, the graph builder is distributed.  Each node's
 * builder sends graph update information to the core server node, which
 * can then operate as if this were a single node implementation.
 * <p>
 * It is expected this implementation will be useful for testing, as it
 * is decoupled from the caching data store.
 * <p>
 * Affinity groups returned by this implementation are of type
 * {@link com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup}.
 * <p>
 * If a node fails or becomes unreachable during a run of the algorithm, no
 * special actions are taken.
 */
package com.sun.sgs.impl.service.nodemap.affinity.dgb;
