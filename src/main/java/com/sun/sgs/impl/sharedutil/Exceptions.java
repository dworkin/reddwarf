/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.sharedutil;

/**
 * Utility methods for working with {@link Exception}s.
 */
public final class Exceptions {

    /** Prevents instantiation of this class. */
    private Exceptions() { }
    
    /**
     * Returns the given exception with its cause initialized.  The
     * original exception is returned in a typesafe way so that it
     * can be thrown easily.
     * 
     * @param <T> the type of the parent exception
     * @param exception the exception to initialize 
     * @param cause the cause
     * @return the exception with its cause initialized
     * 
     * @throws IllegalArgumentException if an attempt is made to set
     *         an exception as its own cause
     * @throws IllegalStateException if the exception has already had
     *         its cause initialized
     * @see Throwable#initCause(Throwable)
     */
    public static <T extends Throwable> T
    initCause(T exception, Throwable cause) {
        exception.initCause(cause);
        return exception;
    }

    /**
     * Returns the caller's stack trace, in the typical format.
     *
     * @return	the caller's stack trace, in the typical format
     */
    public static String getStackTrace() {
	StackTraceElement[] traceElements =
	    Thread.currentThread().getStackTrace();
	StringBuilder buf = new StringBuilder(256);
	for (int i = 1; i < traceElements.length; i++) {
	    buf.append("\tat ").append(traceElements[i]).append("\n");
	}
	return buf.toString();
    }
}
