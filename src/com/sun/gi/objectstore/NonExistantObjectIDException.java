package com.sun.gi.objectstore;

/**
 *
 * <p>Title: NonexistantObjectIDException.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class NonExistantObjectIDException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public NonExistantObjectIDException() {
	super();
    }

    public NonExistantObjectIDException(String message) {
	super(message);
    }

    public NonExistantObjectIDException(String message, Throwable cause) {
	super(message, cause);
    }

    public NonExistantObjectIDException(Throwable cause) {
	super(cause);
    }
}
