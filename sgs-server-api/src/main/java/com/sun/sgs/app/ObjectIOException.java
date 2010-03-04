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

import java.io.IOException;

/**
 * Thrown when an operation fails because of an I/O failure when attempting to
 * access a managed object.  Typically the exception returned by the
 * {@link Throwable#getCause getCause} method will be the {@link IOException}
 * that caused the failure.
 */
public class ObjectIOException extends RuntimeException
    implements ExceptionRetryStatus
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Whether an operation that throws this exception should
     * be retried.
     *
     * @serial
     */
    private final boolean shouldRetry;

    /**
     * Creates an instance of this class with the specified detail message and
     * whether an operation that throws this exception should be retried.
     *
     * @param	message the detail message or <code>null</code>
     * @param	shouldRetry whether an operation that throws this exception
     *		should be retried
     */
    public ObjectIOException(String message, boolean shouldRetry) {
	super(message);
	this.shouldRetry = shouldRetry;
    }

    /**
     * Creates an instance of this class with the specified detail message, the
     * cause, and whether an operation that throws this exception should be
     * retried.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     * @param	shouldRetry whether an operation that throws this exception
     *		should be retried
     */
    public ObjectIOException(
	String message, Throwable cause, boolean shouldRetry)
    {
	super(message, cause);
	this.shouldRetry = shouldRetry;
    }

    /* -- Implement ExceptionRetryStatus -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns the value that was specified for the
     * <code>shouldRetry</code> parameter in the constructor.
     */
    public boolean shouldRetry() {
	return shouldRetry;
    }
}
