/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionAbortedException;

/**
 * A subclass of {@code TransactionAbortedException} whose {@link #shouldRetry
 * shouldRetry} method determines its value based on the cause specified in the
 * constructor.
 */
public class MaybeRetryableTransactionAbortedException
    extends TransactionAbortedException
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public MaybeRetryableTransactionAbortedException(
	String message, Throwable cause)
    {
	super(message, cause);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns {@code true} if the {@code cause} specified
     * in the constructor implements {@link ExceptionRetryStatus}, and calling
     * {@link ExceptionRetryStatus#shouldRetry shouldRetry} on the {@code
     * cause} returns {@code true}.
     */
    public boolean shouldRetry() {
	Throwable cause = getCause();
	return cause instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) cause).shouldRetry();
    }
}
    
