package com.sun.sgs.client;

import java.util.Properties;

public abstract class ClientConnectorFactory {
    public static ClientConnector create(Properties props) {
	// TBI
	return null;
    }

    public static void setDefaultConnector(Class<? extends ClientConnector> clazz) {
	// TBI
    }

    public static void setDefaultLogin(Class<? extends ClientLogin> clazz) {
	// TBI
    }
}
