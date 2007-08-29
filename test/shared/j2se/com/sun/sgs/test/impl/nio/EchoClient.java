/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public class EchoClient {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final String DEFAULT_PORT = "5150";

    private static final int BUFFER_SIZE = 32;
    private static final int NUM_CLIENTS = 900;
    private static final int NUM_WRITES  = 2000;

    static final Logger log = Logger.getAnonymousLogger();
    static CyclicBarrier barrier;
    
    private final AsynchronousChannelGroup group;
    AsynchronousSocketChannel channel;
    int writesLeft;

    protected EchoClient(AsynchronousChannelGroup group) {
        this.group = group;
    }

    public void start() throws IOException {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        try {
            channel = group.provider().openAsynchronousSocketChannel(group);
        } catch (IOException e) {
            log.throwing("EchoClient", "start", e);
            throw e;
        }
        writesLeft = NUM_WRITES;
        channel.connect(new InetSocketAddress(host, port), new ConnectHandler());
    }

    final class ConnectHandler
        implements CompletionHandler<Void, Object>
    {
        public void completed(IoFuture<Void, Object> result) {
            try {
                log.log(Level.FINE, "Connected {0}", channel);
                result.getNow();
                ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
                fillBuffer(buf);
                try {
                    barrier.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (BrokenBarrierException e) {
                    return;
                }
                log.finest("Writing");
                channel.write(buf, buf, new ChannelHandler(channel));
            } catch (ExecutionException e) {
                log.throwing("ConnectHandler", "completed", e);
                // ignore
            }
        }
    }

    static void fillBuffer(ByteBuffer buf) {
        buf.clear();
        buf.putLong(System.nanoTime())
           .putLong(System.nanoTime())
           .putLong(System.nanoTime())
           .putLong(System.nanoTime())
           .flip();
    }

    final class ChannelHandler
        implements CompletionHandler<Integer, ByteBuffer>
    {
        private final AsynchronousSocketChannel channel;
        private boolean writing = true;

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
                if (buf.hasRemaining()) {
                    log.finest("Reading more");
                    channel.read(buf, buf, this);
                } else {
                    writesLeft--;
                    if (writesLeft == 0) {
                        try {
                            channel.close();
                        } catch (IOException ignore) { }
                        disconnected(channel);
                        return;
                    }
                    fillBuffer(buf);
                    log.finest("Writing");
                    writing = true;
                    channel.write(buf, buf, this);
                }
            } catch (ExecutionException e) {
                log.throwing("ChannelHandler", "readCompleted", e);
                // ignore
            }
        }

        public void writeCompleted(IoFuture<Integer, ByteBuffer> result) {
            try {
                int wc = result.getNow();
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
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException e) {
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

        ExecutorService executor =
            Executors.newCachedThreadPool(threadFactory);

        AsynchronousChannelProvider provider =
            AsynchronousChannelProvider.provider();

        AsynchronousChannelGroup group =
            provider.openAsynchronousChannelGroup(executor);

        int numClients = NUM_CLIENTS;
        
        barrier = new CyclicBarrier(numClients + 1);

        for (int i = 0; i < numClients; ++i) {
            EchoClient client = new EchoClient(group);
            client.start();
        }

        barrier.await();
        log.info("Ready to start");
        barrier.await();
        log.info("Finished");

        group.shutdown();
        log.info("Awaiting group termination");        
        if (! group.awaitTermination(20, TimeUnit.SECONDS)) {
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
