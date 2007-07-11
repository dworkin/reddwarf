/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */
package com.sun.sgs.service;

/**
 * Thrown when a node ID used in the system is unknown.
 */
public class UnknownNodeException extends Exception {
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;
    
    /**
     * Constructs an instance of <code>UnknownNodeException</code> with 
     * the specified detail message.
     * 
     * @param message the detail message.
     */
    public UnknownNodeException(String message) {
        super(message);
    }
}
