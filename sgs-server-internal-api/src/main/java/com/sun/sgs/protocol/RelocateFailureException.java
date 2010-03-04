/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
