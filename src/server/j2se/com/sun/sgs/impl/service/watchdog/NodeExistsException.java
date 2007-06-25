/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

/**
 * Thrown when an operation fails because it referred to a node that
 * is currently registered.
 *
 * <p><i>TBD: should this be a RuntimeException?</i>
 *
 * @see	WatchdogServer#registerNode Watchdog.registerNode
 */
public class NodeExistsException extends RuntimeException {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail
     * message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public NodeExistsException(String message) {
	super(message);
    }

    /**
     * Creates an instance of this class with the specified detail
     * message and cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public NodeExistsException(String message, Throwable cause) {
	super(message, cause);
    }
}
