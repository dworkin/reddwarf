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
 * Provides classes used to build graphs used by the label propagation
 * algorithm (LPA).  Graphs for each implementation contain
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex vertices}
 * which represent identities in the system with an attached label, and
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge
 * weighted edges} which represent associations between identities.
 * <p>
 * Each node constructs a 
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.GraphListener} which
 * consumes task information about how identities are associated.  One such
 * association could be identities accessing the same objects.
 * The graph listener creates an
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder}
 * based on the property value {@value
 * com.sun.sgs.impl.service.nodemap.affinity.LPADriver#GRAPH_CLASS_PROPERTY}.
 * The builder ensures that the correct supporting LPA implementation is
 * also instantiated.  The builder can provide some JMX data.
 */
package com.sun.sgs.impl.service.nodemap.affinity.graph;
