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
 * Thrown when an operation fails because there is no current, active
 * transaction.
 */
public class TransactionNotActiveException extends TransactionException
    implements ExceptionRetryStatus
{

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public TransactionNotActiveException(String message) {
	super(message);
    }

    /**
     * Creates an instance of this class with the specified detail message and
     * cause. If {@code cause} implements {@code ExceptionRetryStatus} then its
     * {@code shouldRetry} method will be called when deciding if this
     * exception should be retried.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public TransactionNotActiveException(String message, Throwable cause) {
	super(message, cause);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If a {@code cause} was provided for this exception and it implements
     * {@code ExceptionRetryStatus}, then it will be called to determine if
     * the exception should request to be retried. Otherwise, this will
     * return {@code false}.
     */
    public boolean shouldRetry() {
        Throwable t = getCause();
        if (t == null) {
            return false;
        }
        return (t instanceof ExceptionRetryStatus) &&
            ((ExceptionRetryStatus) t).shouldRetry();
    }

}
