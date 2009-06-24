/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

    /** The ServerSocketFactory used for all exports. */
    private static final ServerSocketFactory ssf = new ServerSocketFactory();

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

        synchronized(ssf) {
            registry = LocateRegistry.createRegistry(port, null, ssf);
            proxy = type.cast(
                    UnicastRemoteObject.exportObject(server, port, null, ssf));
            registry.rebind(name, proxy);
            return ssf.getLastPort();
        }
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

        synchronized(ssf) {
            proxy = type.cast(
                    UnicastRemoteObject.exportObject(server, port, null, ssf));
            return ssf.getLastPort();
        }
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
     * Removes the server from the network. This method will return immediately
     * if called a second time.
     */
    public synchronized void unexport() {
	if (server == null) {
	    return; // return silently
	}
        if (registry != null) {
	    try {
		UnicastRemoteObject.unexportObject(registry, true);
		registry = null;
	    } catch (NoSuchObjectException e) {
		logger.logThrow(
		    Level.FINE, e, "Problem unexporting registry");
		return;
            }
	}
	try {
	    UnicastRemoteObject.unexportObject(server, true);
	    server = null;
	} catch (NoSuchObjectException e) {
	    logger.logThrow(
		Level.FINE, e, "Problem unexporting server");
	    return;
	}
    }
    
    /**
     * Defines a server socket factory that provides access to the server
     * socket's local port.
     */
    private static class ServerSocketFactory
	implements RMIServerSocketFactory
    {
	/** Port number of last server socket created. */
	private static int lastPort = -1;

	/** Creates an instance. */
	ServerSocketFactory() { }

	/** {@inheritDoc} */
	public ServerSocket createServerSocket(int port) throws IOException {
	    ServerSocket serverSocket = new ServerSocket(port);
            lastPort = (serverSocket == null) ?
                -1 : serverSocket.getLocalPort();
	    return serverSocket;
	}

	/**
         * Returns the local port of the last server socket created.
         * This method should only be called once per call to
         * {@link #createServerSocket(int) createServerSocket}.  Subsequent
         * calls will return {@code -1}.
         */
	int getLastPort() {
            int port = lastPort;
            lastPort = -1;
            return port;
        }

        /** {@inheritDoc} */
        public boolean equals(Object rhs) {
            return rhs != null && rhs.getClass().equals(this.getClass());
        }

        /** {@inheritDoc} */
        public int hashCode() {
            return 1;
        }
    }
}   


