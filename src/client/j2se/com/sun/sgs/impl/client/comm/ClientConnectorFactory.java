package com.sun.sgs.impl.client.comm;

import java.util.Properties;

public interface ClientConnectorFactory {
    ClientConnector createConnector(Properties props);
}
