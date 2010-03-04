/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
