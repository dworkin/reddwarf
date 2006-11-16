package com.sun.sgs.client;

import java.nio.ByteBuffer;
import java.util.Properties;

/**
 * A simple abstraction of an SGS Client that can be used
 * to communicate with an SGS backend.
 */
public class SimpleClient implements ServerSession {

    /**
     * Create a new SimpleClient to interact with an instance
     * of the SGS.
     * 
     * @param listener a SimpleClientListener that will receive
     * events for this SimpleClient.
     */
    public SimpleClient(SimpleClientListener listener) {
	// TODO
    }

    /**
     * Initiate a connection to the server.
     * 
     * @param props the Properties to use during this connection.
     */
    public void connect(Properties props) {
	// TODO
    }

    /** {@inheritDoc} */
    public ClientAddress getClientAddress() {
	// TODO
	return null;
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
	// TODO
	return false;
    }

    /** {@inheritDoc} */
    public void logout(boolean force) {
	// TODO
    }

    /** {@inheritDoc} */
    public void send(ByteBuffer message) {
	// TODO
    }
}
