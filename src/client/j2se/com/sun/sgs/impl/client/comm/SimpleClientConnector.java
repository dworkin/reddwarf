package com.sun.sgs.impl.client.comm;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;

import javax.naming.OperationNotSupportedException;

import com.sun.sgs.client.comm.ClientConnectionListener;
import com.sun.sgs.client.comm.ClientConnector;
import com.sun.sgs.impl.io.CompleteMessageFilter;
import com.sun.sgs.impl.io.ConnectorFactory;
import com.sun.sgs.impl.io.PassthroughFilter;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.IOConnector;

/**
 * A basic implementation of a {@code ClientConnector} which uses an 
 * {@code IOConnector} to establish connections.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleClientConnector extends ClientConnector {
    
    private Properties properties;
    private IOConnector connector;
    
    SimpleClientConnector(Properties properties) {
        this.properties = properties;
        String transport = properties.getProperty("transport");
        if (transport == null) {
            transport = "reliable";
        }
        TransportType transportType = transport.equalsIgnoreCase("unreliable") ?
                              TransportType.UNRELIABLE : TransportType.RELIABLE;
        connector = ConnectorFactory.createConnector(transportType);
    }
    
    @Override
    public void cancel() throws IOException {
        // TODO implement
        throw new UnsupportedOperationException("Cancel not yet implemented");
    }

    @Override
    public void connect(ClientConnectionListener connectionListener)
            throws IOException {

        String host = properties.getProperty("host");
        if (host == null) {
            throw new IllegalArgumentException("Missing Property: host");
        }
        String portStr = properties.getProperty("port");
        if (portStr == null) {
            throw new IllegalArgumentException("Missing Property: port");
        }
        int port = Integer.parseInt(portStr);
        if (port <= 0) {
            throw new IllegalArgumentException("Bad port number: " + port);
        }
        
        InetSocketAddress address = new InetSocketAddress(host, port);
        
        SimpleClientConnection connection = 
                                new SimpleClientConnection(connectionListener);
        
        connector.connect(address, connection,
                //new PassthroughFilter()
                new CompleteMessageFilter()
                );
    }

}
