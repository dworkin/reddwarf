/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
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
 * --
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
