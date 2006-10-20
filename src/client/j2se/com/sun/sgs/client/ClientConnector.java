package com.sun.sgs.client;

import java.io.IOException;
import java.util.Properties;

public abstract class ClientConnector {

    private static ClientConnectorFactory theSingletonFactory;

    public static ClientConnector create(Properties props) {
	return theSingletonFactory.createConnector(props);
    }
    
    public static void setConnectorFactory(ClientConnectorFactory factory) {
	theSingletonFactory = factory;
    }
    
    protected ClientConnector() {
	// Empty
    }

    /**
     * Initiates a non-blocking connect to this ClientConnector's target remote address.
     * TODO: more doc
     *
     * @param connectorListener The listener that will receive completion events for this Connector
     *
     * @return a ConnectionHandle that can be used to cancel this connection attempt.
     * 
     * @throws AlreadyConnectedException if this connector is already connected
     * @throws ConnectionPendingException if a non-blocking connection operation is already in progress on this connector
     * @throws ClosedChannelException if this connector is closed
     * @throws UnsupportedAddressTypeException if the type of the given remote address is not supported
     * @throws SecurityException if a security manager has been installed and it does not permit access to the given remote endpoint
     * @throws IOException if some other I/O error occurs
     */
    public abstract void connect(ClientConnectionListener connectionListener) throws IOException;

    /**
     * Cancels a pending connect operation on this ClientConnection.
     *
     * @throws AlreadyConnectedException if this connector is already connected
     * @throws ClosedChannelException if this connector is closed
     * @throws IOException if some other I/O error occurs
     */
    public abstract void cancel() throws IOException;
}
