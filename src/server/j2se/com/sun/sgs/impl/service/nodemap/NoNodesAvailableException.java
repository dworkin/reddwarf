/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.nodemap;

/**
 *
 * Thrown if the {@link NodeAssignPolicy} could not choose a node for
 * assignment because no nodes were available to it.
 * <p>
 * Note that this exception is very specific;  perhaps we'll want a
 * more general exception to be thrown from {@link NodeAssignPolicy#chooseNode}. 
 */
class NoNodesAvailableException extends Exception {
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
