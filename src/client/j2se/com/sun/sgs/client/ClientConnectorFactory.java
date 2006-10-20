package com.sun.sgs.client;

import java.util.Properties;

public interface ClientConnectorFactory {
    ClientConnector createConnector(Properties props);
}
