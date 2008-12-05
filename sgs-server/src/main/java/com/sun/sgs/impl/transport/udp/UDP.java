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

package com.sun.sgs.impl.transport.udp;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.nio.AttachedFuture;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.transport.TransportDescriptorImpl;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a UDP {@link Transport}.
 * The {@link #UDP constructor} supports the following
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
 *      This property is required. The value must be between 1 and 65535.
 * </dl> <p>
 */
public class UDP implements Transport {
    
    private static final String PKG_NAME = "com.sun.sgs.impl.transport.udp";
    
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    private final TransportDescriptor descriptor;
    
    private static class UDPDescriptor extends TransportDescriptorImpl {
        private static final long serialVersionUID = 1L;

        UDPDescriptor(String hostName, int listeningPort) {
             super("UDP",
                   new Delivery[] {Delivery.UNRELIABLE},
                   hostName,
                   listeningPort);
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

    /** The async channel group for this service. */
    private final AsynchronousChannelGroup asyncChannelGroup;

    /** The acceptor for listening for new connections. */
    private final AsynchronousDatagramChannel acceptor;

    /** The currently-active accept operation, or {@code null} if none. */
    volatile IoFuture<?, ?> acceptFuture = null;

    private final ConnectionHandler handler;
    
    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties
     * @param handler
     * @throws java.lang.Exception
     */
    public UDP(Properties properties, ConnectionHandler handler)
	throws Exception
    {	
        assert properties != null;
        assert handler != null;
                
        this.handler = handler;
	logger.log(Level.CONFIG,
	           "Creating UDP transport with properties:{0}",
	           properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	
	try {
	    int port = wrappedProps.getRequiredIntProperty(LISTEN_PORT_PROPERTY,
                                                           1, 65535);
            
	    // Listen for incoming client connections. If no host address
            // is supplied, default to listen on all interfaces.
	    //
            String host = properties.getProperty(LISTEN_HOST_PROPERTY);
            InetSocketAddress listenAddress =
                        host == null ? new InetSocketAddress(port) :
                                       new InetSocketAddress(host, port);
            
            descriptor = new UDPDescriptor(listenAddress.getHostName(),
                                           listenAddress.getPort());
            AsynchronousChannelProvider provider =
                // TODO fetch from config
                AsynchronousChannelProvider.provider();
            asyncChannelGroup =
                // TODO fetch from config
                provider.openAsynchronousChannelGroup(
                    Executors.newCachedThreadPool());
            acceptor =
                    provider.openAsynchronousDatagramChannel(null,
                                                             asyncChannelGroup);
	    try {
                acceptor.bind(listenAddress);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(
			Level.CONFIG, "bound to port:{0,number,#}",
			port);
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
		logger.logThrow(Level.CONFIG, e, "Failed to create transport");
	    }
	    shutdown();
	    throw e;
	}
    }

    /* -- implement Transport -- */
    
    /** {@inheritDoc} */
    @Override
    public TransportDescriptor getDescriptor() {
        return descriptor;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start() {
        if (!acceptor.isOpen())
            throw new IllegalStateException("transport has been shutdown");
        
        if (acceptFuture == null) {
            receive(new AcceptorListener());
            logger.log(Level.FINEST, "transport start");
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public synchronized void shutdown() {
	final IoFuture<?, ?> future = acceptFuture;
	acceptFuture = null;
	if (future != null) {
	    future.cancel(true);
	}

	if (acceptor.isOpen()) {
	    try {
		acceptor.close();
            } catch (IOException e) {
                logger.logThrow(Level.FINEST, e, "closing acceptor throws");
                // swallow exception
            }
	}

	if (!asyncChannelGroup.isShutdown()) {
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
            logger.log(Level.FINEST, "acceptor shutdown");
	}
    }

    private void receive(CompletionHandler<SocketAddress, ByteBuffer> listener)
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        acceptFuture = acceptor.receive(buffer, buffer, listener);
    }
    
    /** A completion handler for accepting initial datagrams. */
    private class AcceptorListener
        implements CompletionHandler<SocketAddress, ByteBuffer>
    {

	/** Handle new connection or report failure. */
        public void completed(IoFuture<SocketAddress, ByteBuffer> result)
        {
            try {
                try {
                    SocketAddress newAddress = result.getNow();
                    logger.log(Level.INFO, "Accepted {0}", newAddress);
                    
                    ByteBuffer buffer = result.attachment();
                    final byte[] firstMessage = new byte[buffer.position()];
                    buffer.position(0);
                    buffer.get(firstMessage, 0, firstMessage.length);
                    
//                    for (int i=0; i< firstMessage.length; i++) {
//                        System.out.println(""+firstMessage[i]);
//                    }
                    
                    AsynchronousDatagramChannel newChannel =
                            AsynchronousDatagramChannel.open(null,
                                                             asyncChannelGroup);
                    newChannel.bind(null);
                    newChannel.connect(newAddress,
                                new ConnectListener(newChannel, firstMessage));
                    
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
                } catch (IOException ignore) {}

                logger.logThrow(
		    Level.SEVERE, e, "acceptor error on {0}", addr);

                // TBD: take other actions, such as restarting acceptor?
            }
            receive(this);
	}
    }
    
    /** A completion handler for datagram channel connect. */
    private class ConnectListener implements CompletionHandler<Void, Void> {

        private final AsynchronousDatagramChannel newChannel;
        private final byte[] firstMessage;
        
        ConnectListener(AsynchronousDatagramChannel newChannel,
                        byte[] firstMessage)
        {
            this.newChannel = newChannel;
            this.firstMessage = firstMessage;
        }
        
        public void completed(IoFuture<Void, Void> result) {
            logger.log(Level.FINEST, "Completed connection ");
            try {
                handler.newConnection(new ChannelWrapper(newChannel,
                                                         firstMessage),
                                      descriptor);

            } catch (CancellationException e) {               
                logger.logThrow(Level.FINE, e, "acceptor cancelled"); 
                //ignore
            } catch (Throwable e) {
                SocketAddress addr = null;
                try {
                    addr = newChannel.getLocalAddress();
                } catch (IOException ignore) {}

                logger.logThrow(
		    Level.SEVERE, e, "connect error on {0}", addr);
            }
	}
    }
    
    /*
     * Wrapper class which returns the initial datagram in the first call
     * to read. Subsequent reads will call through to the wrapped channel.
     */
    private static class ChannelWrapper implements AsynchronousByteChannel {

        private final AsynchronousByteChannel channel;
        private byte[] firstMessage;
        private final Object lock = new Object();
        
        ChannelWrapper(AsynchronousByteChannel channel, byte[] firstMessage) {
            this.channel = channel;
            this.firstMessage = firstMessage;
        }
        
        public <A> IoFuture<Integer, A> read(ByteBuffer dst,
                                             A attachment,
                                             CompletionHandler<Integer, ? super A> handler) {
            synchronized (lock) {
                if (firstMessage != null) {
                    dst.put(firstMessage);
                    int length = firstMessage.length;
                    firstMessage = null;
                    Future<Integer> future =
                            new FirstMessageFuture<Integer>(length);
                    callCompletion(handler, attachment, future);
                    return AttachedFuture.wrap(new FirstMessageFuture<Integer>(length),
                                               attachment);
                }
            }
            return channel.read(dst, attachment, handler);
        }

        // Terrible hack to get around some generics weirdness. Note that
        // the CompletionHandler parameter type no longer includes the
        // "? super" that the read method does above. This removes a
        // complier error when calling handler.completed. Turns out this
        // is what happens in the bowels of the sgs nio implementation, either
        // by design or by accident. I'm not inclined to rip the nio code
        // apart to fix it.
        //
        private <R, A> void callCompletion(CompletionHandler<R, A> handler,
                                           A attachment,
                                           Future<R> future)
        {
            handler.completed(AttachedFuture.wrap(future, attachment));
        }
            
        public <A> IoFuture<Integer, A> read(ByteBuffer dst,
                                             CompletionHandler<Integer, ? super A> handler) {
            return read(dst, null, handler);
        }

        public <A> IoFuture<Integer, A> write(ByteBuffer src,
                                              A attachment,
                                              CompletionHandler<Integer, ? super A> handler) {
            return channel.write(src, attachment, handler);
        }

        public <A> IoFuture<Integer, A> write(ByteBuffer src,
                                              CompletionHandler<Integer, ? super A> handler) {
            return channel.write(src, handler);
        }

        public boolean isOpen() {
            return channel.isOpen();
        }

        public void close() throws IOException {
            channel.close();
        }
    }
    
    private static class FirstMessageFuture<Integer> implements Future<Integer>
    {

        private final Integer size;

        FirstMessageFuture(Integer size) {
            this.size = size;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public boolean isCancelled() {
            return false;
        }

        public boolean isDone() {
            return true;
        }

        public Integer get() throws InterruptedException, ExecutionException {
            return size;
        }

        public Integer get(long arg0, TimeUnit arg1)
            throws InterruptedException, ExecutionException, TimeoutException
        {
            return size;
        }
    }
}