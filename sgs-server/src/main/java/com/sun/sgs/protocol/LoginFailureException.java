 /*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.protocol;

/**
 * An exception that indicates a login request failed.  The {@link
 * Throwable#getMessage getMessage} method returns the reason for the
 * failure, and the {@link Throwable#getCause getCause} method returns the
 * cause of the failure.
 *
 * @see LoginCompletionFuture
 */
public class LoginFailureException extends Exception {

    /** The serial version for this class. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs and instance with the specified detail {@code message}.
     *
     * @param	message a detail message, or {@code null}
     */
    public LoginFailureException(String message) {
	this(message, null);
    }
    
    /**
     * Constructs and instance with the specified detail {@code message}
     * and {@code cause}.
     *
     * @param	message a detail message, or {@code null}
     * @param	cause the cause of this exception, or {@code null}
     */
    public LoginFailureException(String message, Throwable cause) {
	super(message, cause);
    }
}
