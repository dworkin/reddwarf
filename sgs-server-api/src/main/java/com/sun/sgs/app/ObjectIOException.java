/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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
