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

package com.sun.sgs.test.impl.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.StandardSocketOption;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * An echo client for testing.
 */
public class EchoClient {

    /** The default host: {@value} */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** The default port: {@value} */
    public static final String DEFAULT_PORT = "5150";

    /** The default message size: {@value}  */
    public static final String DEFAULT_BUFFER_SIZE = "32";

    /** The default number of clients: {@value} */
    public static final String DEFAULT_NUM_CLIENTS = "4";

    /** The default number of threads: {@value} */
    public static final String DEFAULT_NUM_THREADS = "4";

    /**  The default maximum number of threads: {@value} */
    public static final int DEFAULT_MAX_THREADS = Integer.MAX_VALUE;

    /** The default number of messages to send: {@value} */
    public static final String DEFAULT_NUM_MSGS = "8";

    /** Whether to disable the Nagle algorithm by default: {@value} */
    public static final String DEFAULT_DISABLE_NAGLE = "false";

    private static final int BUFFER_SIZE =
        Integer.valueOf(System.getProperty("buffer_size", DEFAULT_BUFFER_SIZE));
    private static final int NUM_CLIENTS =
        Integer.valueOf(System.getProperty("clients", DEFAULT_NUM_CLIENTS));
    private static final int NUM_MSGS =
        Integer.valueOf(System.getProperty("messages", DEFAULT_NUM_MSGS));
    private static final int NUM_THREADS =
        Integer.valueOf(System.getProperty("threads", DEFAULT_NUM_THREADS));
    private static final int MAX_THREADS =
        Integer.valueOf(System.getProperty("maxthreads",
            String.valueOf(DEFAULT_MAX_THREADS)));
    private static final boolean DISABLE_NAGLE =
        Boolean.valueOf(System.getProperty("tcp_nodelay",
            DEFAULT_DISABLE_NAGLE));

    static final Logger log = Logger.getAnonymousLogger();

    static CountDownLatch startSignal;
    static CountDownLatch doneSignal;

    private final AsynchronousChannelGroup group;
    AsynchronousSocketChannel channel = null;

    int bytesRead = 0;
    int bytesWritten = 0;

    static final AtomicLong totalBytesRead = new AtomicLong();
    static final AtomicLong totalBytesWritten = new AtomicLong();
    static long startTime = 0;

    /**
     * Constructs a new instance of the echo client with the given
     * {@link AsynchronousChannelGroup}.
     * 
     * @param group the asynchronous channel group for this client
     */
    protected EchoClient(AsynchronousChannelGroup group) {
        this.group = group;
    }

    void connect() throws Exception {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        try {
            channel = group.provider().openAsynchronousSocketChannel(group);
            channel.setOption(StandardSocketOption.TCP_NODELAY, DISABLE_NAGLE);
        } catch (IOException e) {
            log.throwing("EchoClient", "connect", e);
            throw e;
        }
        channel.connect(new InetSocketAddress(host, port),
            new ConnectHandler()).get();
    }
 
    void start() throws Exception {
        WriteHandler wh = new WriteHandler();
        ReadHandler rh = new ReadHandler();
        wh.start();
        rh.start();
    }

    final class ConnectHandler
        implements CompletionHandler<Void, Object>
    {
        /**
         * {@inheritDoc}
         */
        public void completed(IoFuture<Void, Object> result) {
            try {
                log.log(Level.FINE, "Connected {0}", channel);
                result.getNow();
                startSignal.countDown();
            } catch (ExecutionException e) {
                log.throwing("ConnectHandler", "completed", e);
                try {
                    channel.close();
                } catch (IOException ioe) {
                    log.throwing("ConnectHandler", "close on exception", ioe);
                }
                startSignal.countDown();
            }
        }
    }

    static ByteBuffer allocateBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity);
    }

    final class ReadHandler
        implements CompletionHandler<Integer, Integer>
    {
        final ByteBuffer buf = allocateBuffer(BUFFER_SIZE);
        
        void start() {
            channel.read(buf, NUM_MSGS - 1, this);
        }

        /**
         * {@inheritDoc}
         */
        public void completed(IoFuture<Integer, Integer> result) {
            try {
                int rc = result.getNow();
                log.log(Level.FINEST, "Read {0} bytes", rc);
                if (rc < 0) {
                    log.log(Level.WARNING, "Read bailed with {0}", rc);
                    disconnected();
                    return;
                }
                bytesRead += rc;
                int opsRemaining = result.attachment();
                if (buf.hasRemaining()) {
                    log.finest("Reading more");
                    channel.read(buf, opsRemaining, this);
                    return;
                }
                if (opsRemaining == 0) {
                    log.finer("Reader finished; closing");
                    disconnected();
                    return;
                }
                buf.clear();
                log.finest("Reading");
                channel.read(buf, opsRemaining - 1, this);
            } catch (ExecutionException e) {
                log.throwing("ReadHandler", "completed", e);
                disconnected();
            } catch (RuntimeException e) {
                log.throwing("ReadHandler", "completed", e);
                disconnected();
            }
        }
    }

    static void fillBuffer(ByteBuffer buf) {
        while (buf.remaining() >= 8) {
            buf.putLong(System.nanoTime());
        }
        byte b = 0;
        while (buf.hasRemaining()) {
            buf.put(b++);
        }
    }

    final class WriteHandler
        implements CompletionHandler<Integer, Integer>
    {
        final ByteBuffer buf = allocateBuffer(BUFFER_SIZE);
        WriteHandler() {
            fillBuffer(buf);
            buf.rewind();
        }
        
        void start() {
            channel.write(buf, NUM_MSGS - 1, this);
        }

        /**
         * {@inheritDoc}
         */
        public void completed(IoFuture<Integer, Integer> result) {
            try {
                int wc =
                    result.getNow();
                log.log(Level.FINEST, "Wrote {0} bytes", wc);
                bytesWritten += wc;
                int opsRemaining = result.attachment();
                if (buf.hasRemaining()) {
                    log.finest("Writing more");
                    channel.write(buf, opsRemaining, this);
                    return;
                }
                if (opsRemaining == 0) {
                    log.finer("Writer finished");
                    return;
                }
                buf.rewind();
                log.finest("Writing");
                channel.write(buf, opsRemaining - 1, this);
            } catch (ExecutionException e) {
                log.throwing("WriteHandler", "completed", e);
                disconnected();
            } catch (RuntimeException e) {
                log.throwing("WriteHandler", "completed", e);
                disconnected();
            }
        }
    }

    void disconnected() {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE,
                "Disconnected {0} read:{1} wrote:{2}",
                new Object[] { channel, bytesRead, bytesWritten });
        }

        try {
            channel.close();
        } catch (IOException ioe) {
            log.throwing("EchoClient", "disconnected", ioe);
        }

        log.log(Level.FINE, "Disconnect done {0}", channel);

        totalBytesRead.addAndGet(bytesRead);
        totalBytesWritten.addAndGet(bytesWritten);
        doneSignal.countDown();
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

        startSignal = new CountDownLatch(NUM_CLIENTS);
        doneSignal = new CountDownLatch(NUM_CLIENTS);

        System.out.format("Connecting %d clients\n", NUM_CLIENTS);

        Set<EchoClient> clients = new HashSet<EchoClient>(NUM_CLIENTS);

        for (int i = 0; i < NUM_CLIENTS; ++i) {
            EchoClient client = new EchoClient(group);
            clients.add(client);
            client.connect();
        }

        startSignal.await();

        System.out.println("Starting test");
        startTime = System.nanoTime();

        for (EchoClient client : clients)
            client.start();

        doneSignal.await();

        long ops = NUM_CLIENTS * NUM_MSGS * 2;
        long elapsed = System.nanoTime() - startTime;
        log.log(Level.INFO, "Bytes read: {0}  written:{1}",
            new Object[] {
                totalBytesRead.get(),
                totalBytesWritten.get()
            });
        System.out.format("%d ops in %d seconds = %d ops/sec\n",
                ops,
                TimeUnit.NANOSECONDS.toSeconds(elapsed),
                TimeUnit.SECONDS.toNanos(ops) / elapsed
            );

        group.shutdown();
        log.info("Awaiting group termination");        
        if (! group.awaitTermination(5, TimeUnit.SECONDS)) {
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
