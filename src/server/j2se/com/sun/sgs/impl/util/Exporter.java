/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides for making the server available on the network, and removing it
 * from the network during shutdown.
 *
 * @param 	<T> the remote interface type
 */
public class Exporter<T extends Remote> {

    /** The name of this class. */
    private static final String CLASSNAME = Exporter.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The type of the server. */
    private final Class<T> type;
    
    /** The server for handling inbound requests. */
    private T server;

    /** The Java(TM) RMI registry for advertising the server. */
    private Registry registry;

    /** The server proxy. */
    private T proxy;

    /** Creates an instance for exporting a remote object of the given
     * {@code type}.
     *
     * @param	type the remote object type
     */
    public Exporter(Class<T> type) {
	this.type = type;
    }

    /**
     * Makes the server available on the network on the specified
     * port, and binds the server's proxy in a registry on the same
     * port with the specified name.  If the port is 0, chooses an
     * anonymous port.  Returns the actual port on which the server is
     * available.
     *
     * @param	server	the server
     * @param	name	the name of the server's proxy in the registry
     * @param	port	the network port for the server
     *
     * @return	the port on which the server is available
     *
     * @throws	IOException if there is a problem exporting the server
     */
    public synchronized int export(T server, String name, int port)
	throws IOException
    {
	if (server == null) {
	    throw new NullPointerException("null server");
	} else if (name == null) {
	    throw new NullPointerException("null name");
	}
	this.server = server;
	assert server != null;
	ServerSocketFactory ssf = new ServerSocketFactory();
	registry = LocateRegistry.createRegistry(port, null, ssf);
	proxy = type.cast(
	    UnicastRemoteObject.exportObject(server, port, null, ssf));
	registry.rebind(name, proxy);
	return ssf.getLocalPort();
    }

    /**
     * Makes the server available on the network on the specified port.  If
     * the port is 0, chooses an anonymous port.  Returns the actual port
     * on which the server is available.
     *
     * @param	server	the server
     * @param	port	the network port for the server
     *
     * @return	the port on which the server is available
     *
     * @throws	IOException if there is a problem exporting the server
     */
    public synchronized int export(T server, int port) throws IOException {
	if (server == null) {
	    throw new NullPointerException("null server");
	}
	this.server = server;
	assert server != null;
	ServerSocketFactory ssf = new ServerSocketFactory();
	proxy =
	    type.cast(UnicastRemoteObject.exportObject(server, port, null, ssf));
	return ssf.getLocalPort();
    }

    /**
     * Returns the exported server's proxy which is available after
     * {@code export} is invoked successfully.  Before the server is
     * exported, this method returns {@code null}.
     *
     * @return	the server's proxy, or {@code null}
     */
    public synchronized T getProxy() {
	return proxy;
    }

    /**
     * Removes the server from the network, returning {@code true} if
     * successful.
     *
     * @return	{@code true} if unexport was successful, and {@code false}
     *		otherwise
     * @throws	IllegalStateException if the server has already been
     *		removed from the network
     */
    public synchronized boolean unexport() {
	if (registry == null) {
	    throw new IllegalStateException(
		"The server is already shut down");
	}
	if (server != null) {
	    try {
		UnicastRemoteObject.unexportObject(server, true);
		server = null;
	    } catch (NoSuchObjectException e) {
		logger.logThrow(
		    Level.FINE, e, "Problem unexporting server");
		return false;
	    }
	}
	if (registry != null ) {
	    try {
		UnicastRemoteObject.unexportObject(registry, true);
		registry = null;
	    } catch (NoSuchObjectException e) {
		logger.logThrow(
		    Level.FINE, e, "Problem unexporting registry");
		return false;
	    }
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


