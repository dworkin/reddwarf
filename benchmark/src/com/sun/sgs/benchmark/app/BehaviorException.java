/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app;

/**
 * Represents an exception thrown in the course of executing a {@code
 * BehaviorModule}.
 */
public class BehaviorException extends Exception {
    
    private static final long serialVersionUID = 0x1L;
    
    public BehaviorException() {
        super();
    }
    
    public BehaviorException(String message) {
        super(message);
    }
    
    public BehaviorException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public BehaviorException(Throwable cause) {
        super(cause);
    }
}
