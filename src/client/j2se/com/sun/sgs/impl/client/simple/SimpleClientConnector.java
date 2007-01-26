package com.sun.sgs.impl.client.simple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Properties;

import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.io.Connector;

/**
 * A basic implementation of a {@code ClientConnector} which uses an 
 * {@code Connector} to establish connections.
 */
class SimpleClientConnector extends ClientConnector {
    
    private final Connector<SocketAddress> connector;
    
    SimpleClientConnector(Properties properties) {
        String transport = properties.getProperty("transport", "reliable");
        
        String host = properties.getProperty("host");
        if (host == null) {
            throw new IllegalArgumentException("Missing Property: host");
        }
        
        String portStr = properties.getProperty("port");
        if (portStr == null) {
            throw new IllegalArgumentException("Missing Property: port");
        }
        int port = Integer.parseInt(portStr);
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Bad port number: " + port);
        }
        
        TransportType transportType =
            transport.equalsIgnoreCase("unreliable")
                ? TransportType.UNRELIABLE
                : TransportType.RELIABLE;
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        connector = 
            new SocketEndpoint(socketAddress, transportType).createConnector();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel() throws IOException {
        // TODO implement
        throw new UnsupportedOperationException("Cancel not yet implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect(ClientConnectionListener connectionListener)
        throws IOException
    {
        SimpleClientConnection connection = 
            new SimpleClientConnection(connectionListener);
        
        connector.connect(connection);
    }

}
