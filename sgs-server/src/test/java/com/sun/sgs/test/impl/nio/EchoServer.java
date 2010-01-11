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

/**
 * An echo server for testing.
 */
public class EchoServer {

    /** The default host address to listen on: {@value} */
    public static final String DEFAULT_HOST = "0.0.0.0";

    /** The default port to listen on: {@value} */
    public static final String DEFAULT_PORT = "5150";

    /** The default message size: {@value}  */
    public static final String DEFAULT_BUFFER_SIZE = "32";
    
    /** The default number of threads: {@value} */
    public static final String DEFAULT_NUM_THREADS = "4";

    /** The default maximum number of threads: {@value} */
    public static final int DEFAULT_MAX_THREADS = Integer.MAX_VALUE;

    /** The default accept() backlog: {@value} */
    public static final String DEFAULT_BACKLOG = "0";

    /** Whether to disable the Nagle algorithm by default: {@value} */
    public static final String DEFAULT_DISABLE_NAGLE = "false";

    private static final int BUFFER_SIZE =
        Integer.valueOf(System.getProperty("buffer_size", DEFAULT_BUFFER_SIZE));
    private static final int NUM_THREADS =
        Integer.valueOf(System.getProperty("threads", DEFAULT_NUM_THREADS));
    private static final int MAX_THREADS =
        Integer.valueOf(System.getProperty("maxthreads",
            String.valueOf(DEFAULT_MAX_THREADS)));
    private static final boolean DISABLE_NAGLE =
        Boolean.valueOf(System.getProperty("tcp_nodelay",
            DEFAULT_DISABLE_NAGLE));

    static final Logger log = Logger.getAnonymousLogger();

    private final AsynchronousChannelGroup group;
    AsynchronousServerSocketChannel acceptor = null;
    private AtomicInteger numConnections = new AtomicInteger();

    /**
     * Constructs a new instance of the echo server with the given
     * {@link AsynchronousChannelGroup}.
     * 
     * @param group the asynchronous channel group for this cserverlient
     */
    protected EchoServer(AsynchronousChannelGroup group) {
        this.group = group;
    }

    void start() throws IOException {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        int backlog = Integer.valueOf(
            System.getProperty("accept_backlog", DEFAULT_BACKLOG));
        try {
            acceptor =
                group.provider().openAsynchronousServerSocketChannel(group);
            acceptor.bind(new InetSocketAddress(host, port), backlog);
            acceptor.accept(new AcceptHandler());
        } catch (IOException e) {
            log.throwing("EchoServer", "start", e);
            throw e;
        }
        System.out.format("Listening on %s\n", acceptor.getLocalAddress());
    }
    
    final class AcceptHandler
        implements CompletionHandler<AsynchronousSocketChannel, Object>
    {
        /**
         * {@inheritDoc}
         */
        public void
        completed(IoFuture<AsynchronousSocketChannel, Object> result) {
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
            channel.setOption(StandardSocketOption.TCP_NODELAY, DISABLE_NAGLE);
        } catch (IOException e) {
            log.throwing("EchoServer", "acceptedChannel", e);
        }
        int nc = numConnections.incrementAndGet();
        if (log.isLoggable(Level.FINER)) {
            log.log(Level.FINER, "Currently {0} connections", nc);
        }
        if (log.isLoggable(Level.INFO)) {
            if (nc % 100 == 0)
               log.log(Level.INFO, "Currently {0} connections", nc);
        }

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

        /**
         * {@inheritDoc}
         */
        public void completed(IoFuture<Integer, ByteBuffer> result) {
            if (writing) {
                writeCompleted(result);
            } else {
                readCompleted(result);
            }
        }

        void readCompleted(IoFuture<Integer, ByteBuffer> result) {
            try {
                int rc = result.getNow();
                log.log(Level.FINEST, "Read {0} bytes", rc);
                if (rc < 0) {
                    log.log(Level.FINE, "{0} read {1} bytes",
                        new Object[] { channel, rc} );
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
                disconnected(channel);
            } catch (RuntimeException e) {
                log.throwing("ChannelHandler", "readCompleted", e);
                disconnected(channel);
            }
        }

        void writeCompleted(IoFuture<Integer, ByteBuffer> result) {
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
                disconnected(channel);
            } catch (RuntimeException e) {
                log.throwing("ChannelHandler", "writeCompleted", e);
                disconnected(channel);
            }
        }
    }

    void disconnected(AsynchronousSocketChannel channel) {
        int nConn = numConnections.decrementAndGet();

        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Disconnected {0}, {1} connections open",
                new Object[] { channel, nConn });
        }

        try {
            channel.close();
        } catch (IOException e) {
            log.throwing("EchoServer", "disconnected", e);
        }

        log.log(Level.FINE, "Disconnect done {0}", channel);
        
        if (nConn == 0) {
            log.info("Closing acceptor");
            try {
                acceptor.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "on acceptor close", e);
            }
            log.info("Shutting down group");
            group.shutdown();
        }
    }

    /**
     * Runs the IO server test.
     *
     * @param args the commandline arguments
     * @throws Exception if an error occurs
     */
    public final static void main(String[] args) throws Exception {

        ThreadFactory threadFactory =
            new VerboseThreadFactory(log, Executors.defaultThreadFactory());

        ThreadPoolExecutor executor = (ThreadPoolExecutor)
            ((MAX_THREADS == Integer.MAX_VALUE)
                 ? Executors.newCachedThreadPool(threadFactory)
                 : Executors.newFixedThreadPool(MAX_THREADS, threadFactory));

        executor.setKeepAliveTime(10, TimeUnit.SECONDS);
        executor.setCorePoolSize(NUM_THREADS);

        log.log(Level.INFO,
            "Prestarting {0,number,integer} threads", NUM_THREADS);

        executor.prestartAllCoreThreads();

        AsynchronousChannelProvider provider =
            AsynchronousChannelProvider.provider();

        AsynchronousChannelGroup group =
            provider.openAsynchronousChannelGroup(executor);

        log.log(Level.INFO, "ChannelGroup is a {0}", group.getClass());

        log.info("Starting the server");

        EchoServer server = new EchoServer(group);
        server.start();

        log.info("Awaiting group termination");        
        if (! group.awaitTermination(3600, TimeUnit.SECONDS)) {
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
