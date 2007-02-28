/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app;

/**
 * Implemented by exception classes that want to control whether an operation
 * that throws an exception of that exception should be retried.
 */
public interface ExceptionRetryStatus {

    /**
     * Provides information about whether an operation that threw this
     * exception should be retried.
     *
     * @return	<code>true</code> if the operation should be retried, else
     *		<code>false</code> 
     */
    boolean shouldRetry();
}
