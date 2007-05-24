/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store;

/**
 * Thrown when no class information is found to be associated with a class ID.
 *
 * @see	DataStore#getClassInfo DataStore.getClassInfo
 */
public class ClassInfoNotFoundException extends Exception {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public ClassInfoNotFoundException(String message) {
	super(message);
    }

    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public ClassInfoNotFoundException(String message, Throwable cause) {
	super(message, cause);
    }
}
