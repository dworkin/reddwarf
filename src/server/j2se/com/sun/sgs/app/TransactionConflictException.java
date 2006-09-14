package com.sun.sgs.app;

/**
 * Thrown when an operation fails because the system aborted the current
 * transaction when it detected a conflict with another transaction.
 */
public class TransactionConflictException
    extends TransactionAbortedException
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public TransactionConflictException(String message) {
	super(message);
    }

    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public TransactionConflictException(String message, Throwable cause) {
	super(message, cause);
    }
}
