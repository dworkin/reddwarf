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
