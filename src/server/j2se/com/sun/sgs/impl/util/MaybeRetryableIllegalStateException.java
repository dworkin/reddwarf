package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;

/**
 * A subclass of <code>IllegalStateException</code> that implements
 * <code>ExceptionRetryStatus</code>, and whose {@link #shouldRetry
 * shouldRetry} method determines its value based on the cause specified in the
 * constructor.
 */
public class MaybeRetryableIllegalStateException extends IllegalStateException
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
    public MaybeRetryableIllegalStateException(
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
	return getCause() instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) cause).shouldRetry();
    }
}
    
