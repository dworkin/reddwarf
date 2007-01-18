package com.sun.sgs.impl.client.simple;

import java.util.Properties;

import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.client.comm.ClientConnectorFactory;

/**
 * A basic ClientConnectorFactory that simply creates a new 
 * {@code SimpleClientConnector} for each call to {@code createConnector}.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleConnectorFactory implements ClientConnectorFactory {

    /**
     * {@inheritDoc}
     */
    public ClientConnector createConnector(Properties props) {
        return new SimpleClientConnector(props);
    }

}
