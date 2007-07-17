/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

/**
 * Provides for making the server available on the network, and removing it
 * from the network during shutdown.
 */
public class Exporter<T extends Remote> {

    /** The server for handling inbound requests. */
    private T server;

    /** The Java(TM) RMI registry for advertising the server. */
    private Registry registry;

    /** Creates an instance. */
    public Exporter() { }

    /**
     * Makes the server available on the network on the specified port.  If
     * the port is 0, chooses an anonymous port.  Returns the actual port
     * on which the server is available.
     */
    public int export(T server, String name, int port) throws IOException {
	this.server = server;
	assert server != null;
	ServerSocketFactory ssf = new ServerSocketFactory();
	registry = LocateRegistry.createRegistry(port, null, ssf);
	registry.rebind(
	    name,
	    UnicastRemoteObject.exportObject(server, port, null, ssf));
	return ssf.getLocalPort();
    }

    /**
     * Removes the server from the network, returning true if successful.
     * Throws IllegalStateException if the server has already been removed
     * from the network.
     */
    public boolean unexport() {
	if (registry == null) {
	    throw new IllegalStateException(
		"The server is already shut down");
	}
	if (server != null) {
	    try {
		UnicastRemoteObject.unexportObject(server, true);
		server = null;
	    } catch (NoSuchObjectException e) {
		/*
		logger.logThrow(
		    Level.FINE, e, "Problem unexporting server");
		*/
		return false;
	    }
	}
	try {
	    UnicastRemoteObject.unexportObject(registry, true);
	    registry = null;
	} catch (NoSuchObjectException e) {
	    /*
	    logger.logThrow(
		Level.FINE, e, "Problem unexporting registry");
	    */
	    return false;
	}
	return true;
    }
    
    /**
     * Defines a server socket factory that provides access to the server
     * socket's local port.
     */
    private static class ServerSocketFactory
	implements RMIServerSocketFactory
    {
	/** The last server socket created. */
	private ServerSocket serverSocket;

	/** Creates an instance. */
	ServerSocketFactory() { }

	/** {@inheritDoc} */
	public ServerSocket createServerSocket(int port) throws IOException {
	    serverSocket = new ServerSocket(port);
	    return serverSocket;
	}

	/** Returns the local port of the last server socket created. */
	int getLocalPort() {
	    return (serverSocket == null) ? -1 : serverSocket.getLocalPort();
	}
    }
}   


