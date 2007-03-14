/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.client.comm;

import java.util.Properties;

/**
 * Factory for concrete implementations of {@link ClientConnector}.
 */
public interface ClientConnectorFactory {

    /**
     * Creates a new instance of {@link ClientConnector} based on the given
     * {@code properties}.
     * 
     * @param properties which affect the implementation of
     *        {@code ClientConnector} returned
     * @return a {@code ClientConnector}
     */
    ClientConnector createConnector(Properties properties);
}
