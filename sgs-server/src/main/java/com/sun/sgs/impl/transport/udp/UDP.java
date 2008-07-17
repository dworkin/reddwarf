package com.sun.sgs.impl.transport.udp;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a UDP transport.
 */
public class UDP implements Transport {
    
    public static final String PKG_NAME = "com.sun.sgs.impl.transport.udp";
    
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /**
     * The server listen address property.
     * This is the host interface we are listening on. Default is listen
     * on all interfaces.
     */
    private static final String LISTEN_HOST_PROPERTY =
        PKG_NAME + ".listen.address";
    
    /** The name of the server port property. */
    private static final String LISTEN_PORT_PROPERTY = com.sun.sgs.impl.kernel.StandardProperties.APP_PORT;
//	PKG_NAME + ".listen.port";

    /** The async channel group for this service. */
    private final AsynchronousChannelGroup asyncChannelGroup;

    /** The acceptor for listening for new connections. */
    private final AsynchronousDatagramChannel acceptor;

    /** The currently-active accept operation, or {@code null} if none. */
    volatile IoFuture<?, ?> acceptFuture;

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
	    /*
	     * Listen for incoming client connections. If no host address
             * is supplied, default to listen on all interfaces.
	     */
            String hostAddress = properties.getProperty(LISTEN_HOST_PROPERTY);
            InetSocketAddress listenAddress =
                hostAddress == null ? new InetSocketAddress(port) :
                                      new InetSocketAddress(hostAddress, port);
            AsynchronousChannelProvider provider =
                // TODO fetch from config
                AsynchronousChannelProvider.provider();
            asyncChannelGroup =
                // TODO fetch from config
                provider.openAsynchronousChannelGroup(
                    Executors.newCachedThreadPool());
            acceptor =
                provider.openAsynchronousDatagramChannel(null, asyncChannelGroup);
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
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create transport");
	    }
	    shutdown();
	    throw e;
	}
    
        receive(new AcceptorListener());

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

    private void receive(CompletionHandler<SocketAddress, ByteBuffer> listener) {
        System.out.println("calling receive with buffer size of " + 512);
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(512);
        acceptor.receive(buffer, buffer, listener);
    }
    
    /** {@inheritDoc} */
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

    /** A completion handler for accepting initial (login) datagrams. */
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
                    System.out.println("msg size(position)= "+buffer.position());

                    final byte[] firstMessage = new byte[buffer.position()];
                    buffer.position(0);
                    buffer.get(firstMessage, 0, firstMessage.length);
                    
//                    for (int i=0; i< firstMessage.length; i++) {
//                        System.out.println(""+firstMessage[i]);
//                    }
                    
                    AsynchronousDatagramChannel newChannel =
                            AsynchronousDatagramChannel.open(null, asyncChannelGroup);
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
                } catch (IOException ioe) {
                    // ignore
                }

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
            System.out.println("Completed connection ");
            try {
                handler.newConnection(new ChannelWrapper(newChannel,
                                                         firstMessage));

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

                // TBD: take other actions, such as restarting acceptor?
            }
	}
    }
    
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
                    IoFuture<Integer, A> result =
                            new FirstMessageFuture<Integer, A>(firstMessage.length,
                                                               attachment);
//                    handler.completed(result);
                    throw new RuntimeException("need to fix this");
//                    firstMessage = null;
//                    return result;
                }
            }
            return channel.read(dst, attachment, handler);
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
    
    private static class FirstMessageFuture<Integer, A> implements IoFuture<Integer, A> {

        private volatile A attachment;
        private final Integer size;

        FirstMessageFuture(Integer size, A attachment) {
            this.attachment = attachment;
            this.size = size;
        }
        
       public Integer getNow() throws ExecutionException {
            return size;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public A attachment() {
            return attachment;
        }

        public A attach(A ob) {
            A previous = attachment;
            attachment = ob;
            return previous;
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