package com.sun.gi.comm.users.client;

/**
 * ClientAlreadyConnectedException is thrown if an attempt is made to
 * connect an already-connected ClientConnectionManager
 * </p>
 * 
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public class ClientAlreadyConnectedException extends Exception {

    private static final long serialVersionUID = 1L;

    public ClientAlreadyConnectedException() {
        super();
    }

    /**
     * ClientAlreadyConnectedException
     * 
     * @param msg an explainatory message to include with the exception.
     */
    public ClientAlreadyConnectedException(String msg) {
        super(msg);
    }
}
