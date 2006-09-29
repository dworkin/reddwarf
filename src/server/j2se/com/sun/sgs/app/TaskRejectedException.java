package com.sun.sgs.app;

/**
 * Thrown when an attempt to schedule a task fails because the {@link
 * TaskManager} refuses to accept the task due to resource limitations.
 */
public class TaskRejectedException extends RuntimeException
    implements ExceptionRetryStatus
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public TaskRejectedException(String message) {
	super(message);
    }

    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public TaskRejectedException(String message, Throwable cause) {
	super(message, cause);
    }

    /* -- Implement ExceptionRetryStatus -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation always returns <code>true</code>.
     */
    public boolean shouldRetry() {
	return true;
    }
}
