/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */
package com.sun.sgs.service;

import com.sun.sgs.auth.Identity;
/**
 * Thrown to indicate that an {@link Identity} is unknown.
 */
public class UnknownIdentityException extends Exception {  
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;
    
    /**
     * Constructs an instance of <code>UnknownIdentityException</code> with 
     * the specified detail message.
     * 
     * @param message the detail message or <code>null</code>
     */
    public UnknownIdentityException(String message) {
        super(message);
    }
    
    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or {@code null}
     * @param	cause the cause or {@code null}
     */
    public UnknownIdentityException(String message, Throwable cause) {
	super(message, cause);
    }
}
