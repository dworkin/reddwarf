/*
 * UnsupportedCallbackException.java
 *
 * Created on January 29, 2006, 3:58 PM
 *
 *
 */

package com.sun.gi.utils.jme;

/**
 *
 * @author as93050
 */
public class UnsupportedCallbackException extends Exception {
    
    /**
     * @serial
     */
    private Callback callback;

    /**
     * Constructs a <code>UnsupportedCallbackException</code>
     * with no detail message.
     *
     * <p>
     *
     * @param callback the unrecognized <code>Callback</code>.
     */
    public UnsupportedCallbackException(Callback callback) {
	super();
	this.callback = callback;
    }

    /**
     * Constructs a UnsupportedCallbackException with the specified detail
     * message.  A detail message is a String that describes this particular
     * exception.
     *
     * <p>
     *
     * @param callback the unrecognized <code>Callback</code>. <p>
     *
     * @param msg the detail message.
     */
    public UnsupportedCallbackException(Callback callback, String msg) {
	super(msg);
	this.callback = callback;
    }

    /**
     * Get the unrecognized <code>Callback</code>.
     *
     * <p>
     *
     * @return the unrecognized <code>Callback</code>.
     */
    public Callback getCallback() {
	return callback;
    }
    
}
