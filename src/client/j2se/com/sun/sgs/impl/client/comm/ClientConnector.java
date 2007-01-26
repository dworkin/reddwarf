package com.sun.sgs.impl.client.comm;

import java.io.IOException;
import java.util.Properties;

/**
 * An abstract mechanism for actively initiating a {@link ClientConnection}.
 */
public abstract class ClientConnector
{
    /** The static singleton factory. */
    private static ClientConnectorFactory theSingletonFactory =
        new com.sun.sgs.impl.client.simple.SimpleConnectorFactory();

    /**
     * Creates a {@code ClientConnector} according to the given
     * {@code properties}.
     *
     * @param properties which affect the implementation of
     *        {@code ClientConnector} returned
     * @return a {@code ClientConnector}
     */
    public static ClientConnector create(Properties properties) {
	return theSingletonFactory.createConnector(properties);
    }

    /**
     * Sets the {@link ClientConnectorFactory} that will be used
     * to create new {@code ClientConnector}s.
     *
     * @param factory the factory to create new {@code ClientConnector}s
     */
    protected static void setConnectorFactory(ClientConnectorFactory factory) {
	theSingletonFactory = factory;
    }

    /**
     * Only allow construction by subclasses.
     */
    protected ClientConnector() {
	// empty
    }

    /**
     * Actively initates a connection to the target remote address.
     * This call is non-blocking. {@link ClientConnectionListener#connected}
     * will be called asynchronously on the {@code listener} upon successful
     * connection, or {@link ClientConnectionListener#disconnected} if it
     * fails.
     *
     * @param listener the listener for all IO events on the
     *        connection, including the result of the connection attempt
     *
     * @throws IOException if an IO error occurs synchronously
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit access to the remote endpoint
     */
    public abstract void connect(ClientConnectionListener listener)
            throws IOException;

    /**
     * Cancels a pending connect operation on this {@code ClientConnecton}.
     *
     * @throws IOException if an IO error occurs synchronously
     */
    public abstract void cancel() throws IOException;

}
