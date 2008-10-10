/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.transport.tcp;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.transport.TransportDescriptorImpl;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a TCP {@link Transport}.
 * The {@link #TCP constructor} supports the following
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
 *	<i>Default:</i> Required property<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the network port that the transport instance will listen on.
 *      This property is required. The value must be between 1 and 65535.<p>
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
public class TCP implements Transport {
 
    public static final String PKG_NAME = "com.sun.sgs.impl.transport.tcp";
    
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));
    
    private final TCPDescriptor descriptor;
    
    private static class TCPDescriptor extends TransportDescriptorImpl {
        private static final long serialVersionUID = 1L;

        TCPDescriptor(String hostName, int listeningPort) {
            super("TCP", Delivery.values(), hostName, listeningPort);
        }

        @Override
        public boolean canSupport(Delivery required) {
            return true; // optimization, TCP supports all
        }

        @Override
        public boolean isCompatibleWith(TransportDescriptor descriptor) {
            return getType().equals(descriptor.getType());
        }
    }
    
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

    /** The name of the acceptor backlog property. */
    public static final String ACCEPTOR_BACKLOG_PROPERTY =
        PKG_NAME + ".acceptor.backlog";

    /** The default acceptor backlog (&lt;= 0 means default). */
    private static final int DEFAULT_ACCEPTOR_BACKLOG = 0;

    private int acceptorBacklog;
    
    /** The async channel group for this service. */
    private AsynchronousChannelGroup asyncChannelGroup;

    /** The acceptor for listening for new connections. */
    private AsynchronousServerSocketChannel acceptor;

    /** The currently-active accept operation, or {@code null} if none. */
    volatile IoFuture<?, ?> acceptFuture;
    
    /** The connection handler. */
    private ConnectionHandler handler;
    
    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties
     * @param handler
     * @throws java.lang.Exception
     */
    public TCP(Properties properties, ConnectionHandler handler)
	throws Exception
    {
        assert properties != null;
        assert handler != null;
                
	logger.log(Level.CONFIG,
	           "Creating TCP transport with properties:{0}",
	           properties);
        
        this.handler = handler;

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        acceptorBacklog = wrappedProps.getIntProperty(
                    ACCEPTOR_BACKLOG_PROPERTY, DEFAULT_ACCEPTOR_BACKLOG);
        
        String host = properties.getProperty(LISTEN_HOST_PROPERTY);
        int port = wrappedProps.getRequiredIntProperty(LISTEN_PORT_PROPERTY,
                                                       1, 65535);
        descriptor = new TCPDescriptor(host, port);
        start();
    }
   
    private void start() {
        try {
             // Listen for incoming client connections. If no host address
             // is supplied, default to listen on all interfaces.
             //
            InetSocketAddress listenAddress =
                        descriptor.getHostName() == null ?
                                new InetSocketAddress(descriptor.getListeningPort()) :
                                new InetSocketAddress(descriptor.getHostName(),
                                                      descriptor.getListeningPort());
            
            AsynchronousChannelProvider provider =
                AsynchronousChannelProvider.provider();
            asyncChannelGroup =
                provider.openAsynchronousChannelGroup(
                    Executors.newCachedThreadPool());
            acceptor =
                provider.openAsynchronousServerSocketChannel(asyncChannelGroup);
	    try {
                acceptor.bind(listenAddress, acceptorBacklog);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(
			Level.CONFIG, "bound to port:{0,number,#}",
			descriptor.getListeningPort());
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
	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create TCP transport");
	    }
	    shutdown();
	    throw new RuntimeException(e);
	}
        acceptFuture = acceptor.accept(new AcceptorListener());
        try {
            if (logger.isLoggable(Level.CONFIG)) {
                logger.log(
                    Level.CONFIG, "listening on {0}",
                    acceptor.getLocalAddress());
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe.getMessage(), ioe);
        }
    }
  
    /** {@inheritDoc} */
    @Override
    public void shutdown() {
	final IoFuture<?, ?> future = acceptFuture;
	acceptFuture = null;
        
	if (future != null) {
	    future.cancel(true);
	}

	if (acceptor != null) {
	    try {
		acceptor.close();
            } catch (IOException e) {
                logger.logThrow(Level.FINEST, e, "closing acceptor throws");
                // swallow exception
            }
	}

	if (asyncChannelGroup != null) {
	    asyncChannelGroup.shutdown();
	    boolean groupShutdownCompleted = false;
	    try {
		groupShutdownCompleted =
		    asyncChannelGroup.awaitTermination(1, TimeUnit.SECONDS);
	    } catch (InterruptedException e) {
		logger.logThrow(Level.FINEST, e,
				"shutdown acceptor interrupted");
		Thread.currentThread().interrupt();
	    }
	    if (!groupShutdownCompleted) {
		logger.log(Level.WARNING, "forcing async group shutdown");
		try {
		    asyncChannelGroup.shutdownNow();
		} catch (IOException e) {
		    logger.logThrow(Level.FINEST, e,
				    "shutdown acceptor throws");
		    // swallow exception
		}
	    }
	}
	logger.log(Level.FINEST, "acceptor shutdown");
    }
  
    /** A completion handler for accepting connections. */
    private class AcceptorListener
        implements CompletionHandler<AsynchronousSocketChannel, Void>
    {

	/** Handle new connection or report failure. */
        @Override
        public void completed(IoFuture<AsynchronousSocketChannel, Void> result)
        {
            try {
                try {
                    AsynchronousSocketChannel newChannel = result.getNow();
                    logger.log(Level.FINER, "Accepted {0}", newChannel);

                    handler.newConnection(newChannel, descriptor);

                    // Resume accepting connections
                    acceptFuture = acceptor.accept(this);

                } catch (ExecutionException e) {
                    throw (e.getCause() == null) ? e : e.getCause();
                }
            } catch (CancellationException e) {               
                logger.logThrow(Level.FINE, e, "acceptor cancelled"); 
                //ignore
            } catch (Throwable e) {
                SocketAddress addr = null;
                try {
                    addr = acceptor.getLocalAddress();
                } catch (IOException ioe) {
                    // ignore
                }
                logger.logThrow(
		    Level.SEVERE, e, "acceptor error on {0}", addr);

                // TBD: take other actions, such as restarting acceptor?
            }
	}
    }
    
    @Override
    public TransportDescriptor descriptor() {
        return descriptor;
    }
}