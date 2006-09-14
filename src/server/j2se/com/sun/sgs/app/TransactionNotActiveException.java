package com.sun.sgs.app;

/**
 * Thrown when an operation fails because there is no current, active
 * transaction.
 */
public class TransactionNotActiveException extends TransactionException {

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
     * cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public TransactionNotActiveException(String message, Throwable cause) {
	super(message, cause);
    }
}
