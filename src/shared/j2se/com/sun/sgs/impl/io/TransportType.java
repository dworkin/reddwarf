/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.io;

/**
 * The type of IO transport: reliable (e.g., TCP), or unreliable (e.g., UDP).
 */
public enum TransportType {
    /** Reliable transport, such as TCP. */
    RELIABLE,
    
    /** Unreliable transport, such as UDP. */
    UNRELIABLE
}
