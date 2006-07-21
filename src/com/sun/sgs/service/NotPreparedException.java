
package com.sun.sgs.service;


/**
 * Thrown when a <code>Service</code>'s <code>commit</code> method is called
 * before that <code>Service</code>'s <code>prepare</code> method has been
 * called for the given transaction.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class NotPreparedException extends Exception {

    /**
     * Creates a new instance of <code>NotPreparedException</code> with
     * no message.
     */
    public NotPreparedException() {
        super();
    }

    /**
     * Creates a new instance of <code>NotPreparedException</code> with
     * the given message.
     *
     * @param message detail about the exception
     */
    public NotPreparedException(String message) {
        super(message);
    }

}
