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
 * Provides classes, interfaces, and utilities used to find affinity groups
 * with the label propagation algorithm (LPA).  Specific implementations,
 * both single- and multi-node, are found in subpackages.
 * <p>
 * An {@link com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup} is a 
 * set of identities which are affiliated with each other and should be
 * co-located on a node.  An
 * {@link com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder} finds
 * such groups, and can provide JMX information on its operations.
 * <p>
 * The {@link com.sun.sgs.impl.service.nodemap.affinity.AbstractLPA} class
 * provides methods used by both single node and distributed implementations
 * of the LPA.  The utility methods in
 * {@link com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupGoodness} can
 * help determine goodness measures of found groups and are useful for testing.
 */
package com.sun.sgs.impl.service.nodemap.affinity;
