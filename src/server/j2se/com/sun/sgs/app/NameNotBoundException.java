package com.sun.sgs.app;

/**
 * Thrown when an operation fails because it referred to a name that was not
 * bound to an object.
 *
 * @see	DataManager#getBinding DataManager.getName
 * @see	DataManager#setBinding DataManager.setName
 * @see	DataManager#removeBinding DataManager.removeName
 */
public class NameNotBoundException extends RuntimeException {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public NameNotBoundException(String message) {
	super(message);
    }

    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public NameNotBoundException(String message, Throwable cause) {
	super(message, cause);
    }
}
