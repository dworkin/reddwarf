/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
