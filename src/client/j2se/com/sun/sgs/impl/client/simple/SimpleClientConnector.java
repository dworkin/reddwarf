/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

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
import com.sun.sgs.impl.sharedutil.MessageBuffer;

/**
 * A basic implementation of a {@code ClientConnector} which uses an 
 * {@code Connector} to establish connections.
 */
class SimpleClientConnector extends ClientConnector {
    
    private final long DEFAULT_CONNECT_TIMEOUT = 5000; 
    
    private final Connector<SocketAddress> connector;
    private final long connectTimeout;
    private Thread connectionWatchdog;
    
    SimpleClientConnector(Properties properties) {
        
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

	String timeoutStr = properties.getProperty("connectTimeout");
	if (timeoutStr == null) {
	    connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	} else {
	    connectTimeout = Long.parseLong(timeoutStr);
	}
        
        // TODO only RELIABLE supported for now.
        TransportType transportType = TransportType.RELIABLE;

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
	connectionWatchdog = new ConnectionWatchdogThread(connectionListener);
	connectionWatchdog.start();
    }

    private class ConnectionWatchdogThread extends Thread {

	private final ClientConnectionListener listener;

	ConnectionWatchdogThread(ClientConnectionListener listener) {
	    super("ConnectionWatchdogThread-" +
		  connector.getEndpoint().toString());
	    this.listener = listener;
	    setDaemon(true);
	}

	public void run() {

	    try {
		connector.waitForConnect(connectTimeout);
		if (!connector.isConnected()) {
		    String reason = "Unable to connect to server";
		    MessageBuffer buf =
			new MessageBuffer(MessageBuffer.getSize(reason));
		    buf.putString(reason);
		    listener.disconnected(false, buf.getBuffer());
		    connector.shutdown();
		}
	    } catch (Exception e) {
		// log exception
	    }
	}
    }
}
