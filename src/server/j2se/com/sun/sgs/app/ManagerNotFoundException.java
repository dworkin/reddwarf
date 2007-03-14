/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app;

/**
 * Thrown when a requested manager is not found.
 *
 * @see		AppContext#getManager AppContext.getManager
 */
public class ManagerNotFoundException extends RuntimeException {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public ManagerNotFoundException(String message) {
	super(message);
    }

    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public ManagerNotFoundException(String message, Throwable cause) {
	super(message, cause);
    }
}
