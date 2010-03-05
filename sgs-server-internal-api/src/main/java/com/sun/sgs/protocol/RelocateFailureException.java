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

package com.sun.sgs.protocol;

/**
 * An exception that indicates a relocation request failed.  The {@link
 * Throwable#getMessage getMessage} method returns a detail message
 * containing an explanation for the failure, the {@link #getReason
 * getReason} method returns the failure reason, and if the failure reason
 * is {@link FailureReason#OTHER}, the {@link Throwable#getCause getCause}
 * method returns the possibly-{@code null} cause of the failure.
 */
public class RelocateFailureException extends Exception {

    /**
     * Reasons why a relocation fails.
     */
    public enum FailureReason {
	/** A duplicate login with the same identity already exists. */
	DUPLICATE_LOGIN,
	/** Other operational failure (see exception {@link
	 * Throwable#getCause cause} for detail). */ 
	SERVER_UNAVAILABLE,
	/** Other operational failure (see exception {@link
	 * Throwable#getCause cause} for detail). */
	OTHER
    };

    /** The serial version for this class. */
    private static final long serialVersionUID = 1L;

    /** The reason for the failure */
    private final FailureReason reason;

    /**
     * Constructs an instance with the specified detail {@code message}
     * and {@code reason}.
     *
     * @param	message a detail message, or {@code null}
     * @param	reason a failure reason
     */
    public RelocateFailureException(String message, FailureReason reason) {
	super(message);
	if (reason == null) {
	    throw new NullPointerException("null reason");
	}
	this.reason = reason;
    }
    
    /**
     * Constructs an instance with the specified detail {@code message}
     * and {@code cause}.
     *
     * @param	message a detail message, or {@code null}
     * @param	cause the cause of this exception, or {@code null}
     */
    public RelocateFailureException(String message, Throwable cause)
    {
	super(message, cause);
	this.reason = FailureReason.OTHER;
    }

    /**
     * Returns a failure reason.  If the returned reason is {@link
     * FailureReason#OTHER}, then the {@link Throwable#getCause cause} may
     * contain an exception that caused the failure.
     *
     * @return a failure reason
     */
    public FailureReason getReason() {
	return reason;
    }
}
