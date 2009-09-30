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
 */

/**
 * Provides classes used to build graphs used by the label propagation
 * algorithm (LPA).  Graphs for each implementation contain
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex vertices}
 * which represent identities in the system with an attached label, and
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge
 * weighted edges} which represent the count of common object uses between
 * identities.
 * <p>
 * Each node constructs a 
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.GraphListener} which
 * consumes task information about which identities are using which objects.
 * The graph listener creates a
 * {@link com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder}
 * based on the property value {@value
 * com.sun.sgs.impl.service.nodemap.affinity.graph.GraphListener#GRAPH_CLASS_PROPERTY}.
 * The builder ensures that the correct supporting LPA implementation is
 * also instantiated.  The builder can provide some JMX data.
 */
package com.sun.sgs.impl.service.nodemap.affinity.graph;
