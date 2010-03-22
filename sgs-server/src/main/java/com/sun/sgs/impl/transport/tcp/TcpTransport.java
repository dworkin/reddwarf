/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.transport.tcp;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.NamedThreadFactory;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a TCP {@link Transport}.
 * The {@link #TcpTransport constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #LISTEN_HOST_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> Listen on all network interfaces
 *
 * <dd style="padding-top: .5em">Specifies the network address the transport
 *      will listen on.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #LISTEN_PORT_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_PORT}<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the network port that the transport instance will listen on.
 *      The value must be between 1 and 65535.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value #ACCEPTOR_BACKLOG_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> 0<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the acceptor backlog. This value is passed as the second
 *      argument to the
 *      {@link AsynchronousServerSocketChannel#bind(SocketAddress,int)
 *      AsynchronousServerSocketChannel.bind} method.
 * </dl> <p>
 */
public class TcpTransport implements Transport {
 
    private static final String PKG_NAME = "com.sun.sgs.impl.transport.tcp";
    
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));
        
    /**
     * The server listen address property.
     * This is the host interface we are listening on. Default is listen
     * on all interfaces.
     */
    public static final String LISTEN_HOST_PROPERTY =
        PKG_NAME + ".listen.address";
    
    /** The name of the server port property. */
    public static final String LISTEN_PORT_PROPERTY =
	PKG_NAME + ".listen.port";

    /** The default port: {@value #DEFAULT_PORT}. */
    public static final int DEFAULT_PORT = 62964;
    
    /** The listen address. */
    final InetSocketAddress listenAddress;
    
    /** The name of the acceptor backlog property. */
    public static final String ACCEPTOR_BACKLOG_PROPERTY =
        PKG_NAME + ".acceptor.backlog";

    /** The default acceptor backlog (&lt;= 0 means default). */
    private static final int DEFAULT_ACCEPTOR_BACKLOG = 0;

    /** The acceptor backlog. */
    private final int acceptorBacklog;
    
    /** The async channel group for this service. */
    private final AsynchronousChannelGroup asyncChannelGroup;

    /** The acceptor for listening for new connections. */
    volatile AsynchronousServerSocketChannel acceptor;

    /** The currently-active accept operation, or {@code null} if none. */
    volatile IoFuture<?, ?> acceptFuture = null;

    /** The acceptor listener. */
    private AcceptorListener acceptorListener = null;
    
    /** The transport descriptor */
    private final TcpDescriptor descriptor;

    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties transport properties
     */
    public TcpTransport(Properties properties) {

        logger.log(Level.CONFIG, "Creating TcpTransport");
        if (properties == null) {
            throw new NullPointerException("properties is null");
        }
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        acceptorBacklog = wrappedProps.getIntProperty(ACCEPTOR_BACKLOG_PROPERTY,
                                                      DEFAULT_ACCEPTOR_BACKLOG);
        
        String host = properties.getProperty(LISTEN_HOST_PROPERTY);
        int port = wrappedProps.getIntProperty(LISTEN_PORT_PROPERTY,
                                               DEFAULT_PORT, 1, 65535);

        try {
            // If no host address is supplied, default to listen on all
            // interfaces on the local host.
            //
            listenAddress =
                        host == null ?
                                new InetSocketAddress(port) :
                                new InetSocketAddress(host, port);
            
            descriptor =
                    new TcpDescriptor(host == null ?
                                      InetAddress.getLocalHost().getHostName() :
                                      host,
                                      listenAddress.getPort());
            AsynchronousChannelProvider provider =
                AsynchronousChannelProvider.provider();
            asyncChannelGroup =
                provider.openAsynchronousChannelGroup(
                    Executors.newCachedThreadPool(
                    new NamedThreadFactory("TcpTransport-Acceptor")));
            acceptor =
                provider.openAsynchronousServerSocketChannel(asyncChannelGroup);
	    try {
                acceptor.bind(listenAddress, acceptorBacklog);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(Level.CONFIG,
                               "acceptor bound to host: {0} port:{1,number,#}",
                               descriptor.hostName,
                               descriptor.listeningPort);
		}
	    } catch (Exception e) {
		logger.logThrow(Level.WARNING, e,
                                "acceptor failed to listen on {0}",
                                listenAddress);
		try {
		    acceptor.close();
                } catch (IOException ioe) {
                    logger.logThrow(Level.WARNING, ioe,
                                    "problem closing acceptor");
                }
		throw e;
	    }

            logger.log(Level.CONFIG,
                       "Created TcpTransport with properties:" +
                       "\n  " + ACCEPTOR_BACKLOG_PROPERTY + "=" +
                       acceptorBacklog +
                       "\n  " + LISTEN_HOST_PROPERTY + "=" + host +
                       "\n  " + LISTEN_PORT_PROPERTY + "=" + port);

	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(Level.CONFIG, e,
                                "Failed to create TCP transport");
	    }
	    shutdown();
	    throw new RuntimeException(e);
	}
    }
  
    /* -- implement Transport -- */
    
    /** {@inheritDoc} */
    public TransportDescriptor getDescriptor() {
        return descriptor;
    }
    
    /** {@inheritDoc} */
    public Delivery getDelivery() {
        return Delivery.RELIABLE;
    }
    
    /** {@inheritDoc} */
    public synchronized void accept(ConnectionHandler handler) {
	if (handler == null) {
	    throw new NullPointerException("null handler");
	} else if (!acceptor.isOpen()) {
	    throw new IllegalStateException("transport has been shutdown");
	}
	
	if (acceptorListener != null) {
	    throw new IllegalStateException("accept already called");
	}
	acceptorListener = new AcceptorListener(handler);
	
        assert acceptFuture == null;
        acceptFuture = acceptor.accept(acceptorListener);
        logger.log(Level.CONFIG, "transport accepting connections");
    }

    /** {@inheritDoc} */
    public synchronized void shutdown() {
	final IoFuture<?, ?> future = acceptFuture;
	acceptFuture = null;
        
	if (future != null) {
	    future.cancel(true);
	}
	
	if (acceptor != null && acceptor.isOpen()) {
	    try {
		acceptor.close();
            } catch (IOException e) {
                logger.logThrow(Level.FINEST, e, "closing acceptor throws");
                // swallow exception
            }
	}

	if (asyncChannelGroup != null && !asyncChannelGroup.isShutdown()) {
	    asyncChannelGroup.shutdown();
	    boolean groupShutdownCompleted = false;
	    try {
		groupShutdownCompleted =
		    asyncChannelGroup.awaitTermination(1, TimeUnit.SECONDS);
	    } catch (InterruptedException e) {
		logger.logThrow(Level.FINEST, e,
				"shutdown async group interrupted");
		Thread.currentThread().interrupt();
	    }
	    if (!groupShutdownCompleted) {
		logger.log(Level.WARNING, "forcing async group shutdown");
		try {
		    asyncChannelGroup.shutdownNow();
		} catch (IOException e) {
		    logger.logThrow(Level.FINEST, e,
				    "shutdown async group throws");
		    // swallow exception
		}
	    }
            logger.log(Level.FINEST, "transport shutdown");
	}
    }

    /**
     * Closes the current acceptor and opens a new one, binding it to the
     * listen address specified during construction.  This method is
     * invoked if a problem occurs handling a new connection or initiating
     * another accept request on the current acceptor.
     *
     * @throws	IOException if the async channel group is shutdown, or
     * 		a problem occurs creating the new acceptor or binding it to
     * 		the listen address
     */
    private synchronized void restart()
            throws IOException
    {
	if (asyncChannelGroup.isShutdown()) {
	    throw new IOException("channel group is shutdown");
	}
	
	try {
	    acceptor.close();
	} catch (IOException ex) {
	    logger.logThrow(Level.FINEST, ex,
			    "exception closing acceptor during restart");
	}
	acceptor = AsynchronousChannelProvider.provider().
	    openAsynchronousServerSocketChannel(asyncChannelGroup);
	
	acceptor.bind(listenAddress, acceptorBacklog);
    }
            
    /** A completion handler for accepting connections. */
    private class AcceptorListener
        implements CompletionHandler<AsynchronousSocketChannel, Void>
    {
	/** The connection handler. */
	private final ConnectionHandler connectionHandler;

	/**
	 * Constructs an instance with the specified {@code connectionHandler}.
	 *
	 * @param connectionHandler a connection handler
	 */
	AcceptorListener(ConnectionHandler connectionHandler) {
	    this.connectionHandler = connectionHandler;
	}
	
	/** Handle new connection or report failure. */
        public void completed(IoFuture<AsynchronousSocketChannel, Void> result)
        {
            try {
                try {
                    AsynchronousSocketChannel newChannel = result.getNow();
                    logger.log(Level.FINER, "Accepted {0}", newChannel);

                    connectionHandler.newConnection(newChannel);

                    // Resume accepting connections
                    acceptFuture = acceptor.accept(this);

                } catch (ExecutionException e) {
                    throw (e.getCause() == null) ? e : e.getCause();
                }
            } catch (CancellationException e) {               
                logger.logThrow(Level.FINE, e, "acceptor cancelled"); 
                //ignore
            } catch (Throwable e) {
                logger.logThrow(Level.SEVERE, e,
                                "acceptor error on {0}", listenAddress);
                try {
                    restart();
                    
                    // Resume accepting connections on new acceptor
                    acceptFuture = acceptor.accept(this);
                } catch (IOException ioe) {
                    logger.logThrow(Level.FINEST, ioe,
                                    "exception during restart");
                    shutdown();
                    connectionHandler.shutdown();
                }
            }
	}
    }
}
