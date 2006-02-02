package com.sun.gi.comm.users.client;

/**
 * <p>Title: ClientAlreadyConnectedException
 * <p>Description: This exception is thrown if an attempt is made to connect an
 * already connected ClientConnectionManager</p>
 * <p>Copyright: Copyright (c) Oct 24, 2005 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public class ClientAlreadyConnectedException extends Exception {

    public ClientAlreadyConnectedException() { }

    /**
     * ClientAlreadyConnectedException
     *
     * @param msg an explainatory message to include with the exception.
     */
    public ClientAlreadyConnectedException(String msg) {
	super(msg);
    }

}
