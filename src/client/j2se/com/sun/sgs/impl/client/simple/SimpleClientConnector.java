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
    private final String DEFAULT_CONNECT_FAILURE_MESSAGE =
	"Unable to connect to server";
    
    private final Connector<SocketAddress> connector;
    private final long connectTimeout;
    private final String connectFailureMessage;
    private Thread connectWatchdog;
    
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
	connectFailureMessage =
	    properties.getProperty(
		"connectFailureMessage", DEFAULT_CONNECT_FAILURE_MESSAGE);
        
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
	connectWatchdog = new ConnectWatchdogThread(connectionListener);
	connectWatchdog.start();
    }

    private class ConnectWatchdogThread extends Thread {

	private final ClientConnectionListener listener;

	ConnectWatchdogThread(ClientConnectionListener listener) {
	    super("ConnectWatchdogThread-" +
		  connector.getEndpoint().toString());
	    this.listener = listener;
	    setDaemon(true);
	}

	public void run() {

	    boolean connectComplete = false;
	    try {
		connectComplete = connector.waitForConnect(connectTimeout);
	    } catch (InterruptedException e) {
		// ignore
	    }
	    try {
		if (! connector.isConnected()) {
		    String reason = connectFailureMessage;
		    MessageBuffer buf =
			new MessageBuffer(MessageBuffer.getSize(reason));
		    buf.putString(reason);
		    listener.disconnected(false, buf.getBuffer());
		    if (! connectComplete) {
			connector.shutdown();
		    }
		}
	    } catch (Exception e) {
		// TBD: log exception
	    }
	}
    }
}
