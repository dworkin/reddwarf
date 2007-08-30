/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.StandardSocketOption;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public class EchoServer {

    public static final String DEFAULT_HOST = "0.0.0.0";
    public static final String DEFAULT_PORT = "5150";

    public static final String DEFAULT_BUFFER_SIZE = "32";
    public static final String DEFAULT_NUM_CLIENTS =  "4";
    public static final String DEFAULT_BACKLOG     =  "0";

    private static final int BUFFER_SIZE =
        Integer.valueOf(System.getProperty("buffer_size", DEFAULT_BUFFER_SIZE));
    private static final int NUM_CLIENTS =
        Integer.valueOf(System.getProperty("clients", DEFAULT_NUM_CLIENTS));

    static final Logger log = Logger.getAnonymousLogger();

    private final AsynchronousChannelGroup group;
    AsynchronousServerSocketChannel acceptor;
    private AtomicInteger numConnections = new AtomicInteger();

    protected EchoServer(AsynchronousChannelGroup group) {
        this.group = group;
    }

    public void start() throws IOException {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        int backlog = Integer.valueOf(System.getProperty("accept_backlog", DEFAULT_BACKLOG));
        try {
            acceptor = group.provider().openAsynchronousServerSocketChannel(group);
            acceptor.bind(new InetSocketAddress(host, port), backlog);
            acceptor.accept(new AcceptHandler());
        } catch (IOException e) {
            log.throwing("EchoServer", "start", e);
            throw e;
        }
        log.log(Level.INFO, "Listening on {0}", acceptor.getLocalAddress());
    }
    
    final class AcceptHandler
        implements CompletionHandler<AsynchronousSocketChannel, Object>
    {
        public void completed(IoFuture<AsynchronousSocketChannel, Object> result) {
            try {
                acceptedChannel(result.getNow());
                //try {
                //    Thread.sleep(50);
                //} catch (InterruptedException e) {
                //    Thread.currentThread().interrupt();
                //}
                acceptor.accept(this);
            } catch (ExecutionException e) {
                log.throwing("AcceptHandler", "completed", e);
                // ignore
            }
        }
    }

    void acceptedChannel(AsynchronousSocketChannel channel) {
        log.log(Level.FINER, "Accepted {0}", channel);
        try {
            channel.setOption(StandardSocketOption.TCP_NODELAY, Boolean.FALSE);
        } catch (IOException e) {
            log.throwing("EchoServer", "acceptedChannel", e);
        }
        int nc = numConnections.incrementAndGet();
        if (nc % 100 == 0)
          log.log(Level.INFO, "Currently {0} connections", nc);

        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        log.finest("Reading");
        channel.read(buf, buf, new ChannelHandler(channel));
    }

    final class ChannelHandler
        implements CompletionHandler<Integer, ByteBuffer>
    {
        private final AsynchronousSocketChannel channel;
        private boolean writing = false;

        ChannelHandler(AsynchronousSocketChannel channel) {
            this.channel = channel;
        }

        public void completed(IoFuture<Integer, ByteBuffer> result) {
            if (! channel.isOpen()) {
                disconnected(channel);
                return;
            }

            if (writing) {
                writeCompleted(result);
            } else {
                readCompleted(result);
            }
        }

        public void readCompleted(IoFuture<Integer, ByteBuffer> result) {
            try {
                int rc = result.getNow();
                log.log(Level.FINEST, "Read {0} bytes", rc);
                if (rc < 0) {
                    disconnected(channel);
                    return;
                }
                ByteBuffer buf = result.attachment();
                buf.flip();
                log.finest("Writing");
                writing = true;
                channel.write(buf, buf, this);
            } catch (ExecutionException e) {
                log.throwing("ChannelHandler", "readCompleted", e);
                // ignore
            }
        }

        public void writeCompleted(IoFuture<Integer, ByteBuffer> result) {
            try {
                int wc =
                    result.getNow();
                log.log(Level.FINEST, "Wrote {0} bytes", wc);
                ByteBuffer buf = result.attachment();
                if (buf.hasRemaining()) {
                    log.finest("Writing more");
                    channel.write(buf, buf, this);
                } else {
                    buf.clear();
                    writing = false;
                    log.finest("Reading");
                    channel.read(buf, buf, this);
                }
            } catch (ExecutionException e) {
                log.throwing("ChannelHandler", "writeCompleted", e);
                // ignore
            }
        }
    }

    public void disconnected(AsynchronousSocketChannel channel) {
        log.log(Level.FINE, "Disconnected {0}", channel);
        
        if (numConnections.decrementAndGet() == 0) {
            log.info("Shutting down group");
            group.shutdown();
        }
    }

    /**
     * Runs the IO server test.
     *
     * @param args the commandline arguments
     */
    public final static void main(String[] args) throws Exception {

        ThreadFactory threadFactory =
            new TestingThreadFactory(log, Executors.defaultThreadFactory());

        ThreadPoolExecutor executor =
            (ThreadPoolExecutor) Executors.newCachedThreadPool(threadFactory);
            
        AsynchronousChannelProvider provider =
            AsynchronousChannelProvider.provider();

        AsynchronousChannelGroup group =
            provider.openAsynchronousChannelGroup(executor);

        log.log(Level.INFO,
            "Prestarting {0,number,integer} threads", NUM_CLIENTS);

        executor.setCorePoolSize(NUM_CLIENTS);
        executor.prestartAllCoreThreads();

        log.info("Starting the server");

        EchoServer server = new EchoServer(group);
        server.start();

        log.info("Awaiting group termination");        
        if (! group.awaitTermination(1, TimeUnit.DAYS)) {
            log.warning("Forcing group termination");
            group.shutdownNow();
            if (! group.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warning("Group could not be forcibly terminated");
            }
        }
        if (group.isTerminated())
            log.info("Group terminated");

        log.info("Terminating executor");
        executor.shutdown();
        log.info("Awaiting executor termination");        
        if (! executor.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warning("Forcing executor termination");
            executor.shutdownNow();
            if (! executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warning("Executor could not be forcibly terminated");
            }
        }
        if (executor.isTerminated())
            log.info("Executor terminated");
    }
}
