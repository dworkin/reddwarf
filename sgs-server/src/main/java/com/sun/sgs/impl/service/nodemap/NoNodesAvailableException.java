/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

/**
 *
 * Thrown if the {@link NodeAssignPolicy} could not choose a node for
 * assignment because no nodes were available to it.
 * <p>
 * Note that this exception is very specific;  perhaps we'll want a more
 * general exception to be thrown from {@link NodeAssignPolicy#chooseNode}. 
 */
public class NoNodesAvailableException extends Exception {
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail
     * message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public NoNodesAvailableException(String message) {
        super(message);
    }
    
    /**
     * Creates an instance of this class with the specified detail
     * message and cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public NoNodesAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
