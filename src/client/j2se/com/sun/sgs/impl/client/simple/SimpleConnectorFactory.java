package com.sun.sgs.impl.client.simple;

import java.util.Properties;

import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.client.comm.ClientConnectorFactory;

/**
 * A trivial ClientConnectorFactory that creates a new 
 * {@code SimpleClientConnector} each time {@code createConnector}
 * is invoked.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
class SimpleConnectorFactory implements ClientConnectorFactory {

    /**
     * {@inheritDoc}
     */
    public ClientConnector createConnector(Properties props) {
        return new SimpleClientConnector(props);
    }

}
