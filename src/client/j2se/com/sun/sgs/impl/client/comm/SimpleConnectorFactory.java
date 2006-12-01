package com.sun.sgs.impl.client.comm;

import java.util.Properties;

import com.sun.sgs.client.comm.ClientConnector;
import com.sun.sgs.client.comm.ClientConnectorFactory;

public class SimpleConnectorFactory implements ClientConnectorFactory {

    public ClientConnector createConnector(Properties props) {
        return new SimpleClientConnector(props);
    }

}
