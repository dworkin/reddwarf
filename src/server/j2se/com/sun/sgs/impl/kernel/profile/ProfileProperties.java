/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

/**
 * A collection of common properties used by {@link
 * ProfileOperationListener} implementations.  
 */
public interface ProfileProperties {

    /**
     * The property for defining the number task to summarize for
     * windowed {@code ProfileOperationListener} implementations.
     * The value assigned to the property must be an integer.
     */
    public static final String WINDOW_SIZE =
	"com.sun.sgs.kernel.profile.listener.window.size";


}