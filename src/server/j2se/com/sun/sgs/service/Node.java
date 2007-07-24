/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */
package com.sun.sgs.service;

/**
 * Information about nodes in the system.
 * 
 */
public interface Node {
        
    /**
     * 
     * Returns the node ID.
     *
     * @return the node ID
     */
    long getId();
    
    /** 
     * Returns {@code true} if the node is known to be alive at the last
     * check, and {@code false} if it is thought to have failed.
     *
     * @return {@code true} if the node is alive, and {@code false} otherwise
     */
    boolean isAlive();
    
    /** 
     * Returns the host name of the node this object represents.
     * <b> Good chance we need something else here. </b>
     *
     * @return information about how to reach the node
     */
    String getHostName();
}
