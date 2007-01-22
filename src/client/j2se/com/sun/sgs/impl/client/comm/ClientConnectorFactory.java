package com.sun.sgs.impl.client.comm;

import java.util.Properties;

/**
 * Factory for concrete implementations of {@link ClientConnector}.
 * 
 * @author Sten Anderson
 */
public interface ClientConnectorFactory {

    /**
     * Create a new instance of {@link ClientConnector} based on the given
     * {@code properties}.
     * 
     * @param properties which affect the implementation of ClientConnector
     *        returned.
     * @return a ClientConnector.
     */
    ClientConnector createConnector(Properties properties);
}
