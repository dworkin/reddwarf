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
 * Provides classes for a multi-node, distributed algorithm implementation
 * of the label propagation algorithm described in "Near linear time algorithm
 * to detect community structures in large-scale networks" by Raghavan, Albert
 * and Kumara (2007).
 * <p>
 * In this implementation, the algorithm is distributed.  Each node's
 * builder maintains a portion of the graph of identities linked by common
 * object uses.  The builders also are notified of data cache conflicts,
 * as reported by the caching data store.
 * <p>
 * The algorithm is driven by the 
 * {@link com.sun.sgs.impl.service.nodemap.affinity.dlpa.LPAServer}, which
 * instructs each
 * {@link com.sun.sgs.impl.service.nodemap.affinity.dlpa.LPAClient} when to
 * start an algorithm run and synchronizes each iteration of the algorithm.
 * Information about affinity groups are communicated as the serializable
 * {@link com.sun.sgs.impl.service.nodemap.affinity.AffinitySet}.
 * <p>
 * Affinity groups returned by this implementation are of type
 * {@link com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup}.
 * <p>
 * If a node fails or becomes unreachable during a run of the algorithm, the
 * run is deemed failed and invalid.  No attempt is made to mark unreachable
 * nodes as failed within the Darkstar cluster.
 */
package com.sun.sgs.impl.service.nodemap.affinity.dlpa;

