/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.kernel;

/**
 * The valid node types.
 */
public enum NodeType {
    /** A single node configuration. */
    singleNode,
    /** The core server for multi-node configurations. */
    coreServerNode,
    /** An application node for multi-node configurations. */
    appNode,
}
