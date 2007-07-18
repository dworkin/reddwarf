/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionNotActiveException;

/**
 * A subclass of <code>TransactionNotActiveException</code> that implements
 * <code>ExceptionRetryStatus</code>, and whose {@link #shouldRetry
 * shouldRetry} method determines its value based on the cause specified in the
 * constructor.
 */
public class MaybeRetryableTransactionNotActiveException
    extends TransactionNotActiveException
    implements ExceptionRetryStatus
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
    public MaybeRetryableTransactionNotActiveException(
	String message, Throwable cause)
    {
	super(message, cause);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns <code>true</code> if the <code>cause</code>
     * specified in the constructor implements {@link ExceptionRetryStatus},
     * and calling {@link ExceptionRetryStatus#shouldRetry shouldRetry} on the
     * <code>cause</code> returns <code>true</code>.
     */
    public boolean shouldRetry() {
	Throwable cause = getCause();
	return cause instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) cause).shouldRetry();
    }
}
    
