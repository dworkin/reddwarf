/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.client.simple;

import java.util.Properties;

import com.sun.sgs.impl.client.comm.ClientConnectorFactory;

/**
 * A trivial ClientConnectorFactory that creates a new 
 * {@code SimpleClientConnector} each time {@code createConnector}
 * is invoked.
 */
public class SimpleConnectorFactory implements ClientConnectorFactory {

    /**
     * Creates a new instance of {@code SimpleClientConnector} based on
     * the given {@code properties}.
     * 
     * @param properties which affect the creation of the
     *        {@code SimpleClientConnector} returned
     * @return a {@code SimpleClientConnector}
     */
    public SimpleClientConnector createConnector(Properties properties) {
        return new SimpleClientConnector(properties);
    }

}
