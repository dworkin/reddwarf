package com.sun.sgs.client.comm;

import java.util.Properties;

public interface ClientConnectorFactory {
    ClientConnector createConnector(Properties props);
}
