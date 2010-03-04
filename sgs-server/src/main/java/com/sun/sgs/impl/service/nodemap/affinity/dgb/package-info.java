/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
