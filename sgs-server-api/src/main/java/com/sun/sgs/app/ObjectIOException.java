/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
