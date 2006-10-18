package com.sun.sgs.client;

import java.util.Properties;

public abstract class ClientConnectorFactory {
    public static ClientConnector createConnector(Properties props) {
	return theSingletonFactory.create(props);
    }

    protected static void setConnectorFactory(ClientConnectorFactory factory) {
	theSingletonFactory = factory;
    }

    private static ClientConnectorFactory theSingletonFactory;
    
    protected abstract ClientConnector create(Properties props);
}
