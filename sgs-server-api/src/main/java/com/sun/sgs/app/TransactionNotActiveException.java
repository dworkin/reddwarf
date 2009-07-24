/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
