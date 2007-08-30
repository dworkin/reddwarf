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

public class EchoClient {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final String DEFAULT_PORT = "5150";

    public static final String DEFAULT_BUFFER_SIZE = "32";
    public static final String DEFAULT_NUM_CLIENTS =  "4";
    public static final String DEFAULT_NUM_MSGS    =  "8";

    private static final int BUFFER_SIZE =
        Integer.valueOf(System.getProperty("buffer_size", DEFAULT_BUFFER_SIZE));
    private static final int NUM_CLIENTS =
        Integer.valueOf(System.getProperty("clients", DEFAULT_NUM_CLIENTS));
    private static final int NUM_MSGS =
        Integer.valueOf(System.getProperty("messages", DEFAULT_NUM_MSGS));

    static final Logger log = Logger.getAnonymousLogger();
    static CyclicBarrier barrier;
    
    private final AsynchronousChannelGroup group;
    AsynchronousSocketChannel channel;

    int bytesRead = 0;
    int bytesWritten = 0;

    static final AtomicLong totalBytesRead = new AtomicLong();
    static final AtomicLong totalBytesWritten = new AtomicLong();
    static long startTime = 0;

    protected EchoClient(AsynchronousChannelGroup group) {
        this.group = group;
    }

    public void start() throws Exception {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        try {
            channel = group.provider().openAsynchronousSocketChannel(group);
            channel.setOption(StandardSocketOption.TCP_NODELAY, Boolean.FALSE);
        } catch (IOException e) {
            log.throwing("EchoClient", "start", e);
            throw e;
        }
        channel.connect(new InetSocketAddress(host, port), new ConnectHandler()).get();
    }

    final class ConnectHandler
        implements CompletionHandler<Void, Object>
    {
        public void completed(IoFuture<Void, Object> result) {
            try {
                log.log(Level.FINE, "Connected {0}", channel);
                result.getNow();
                WriteHandler wh = new WriteHandler();
                ReadHandler rh = new ReadHandler();
                try {
                    barrier.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warning("Barrier interrupted");
                    return;
                } catch (BrokenBarrierException e) {
                    log.warning("Barrier broken");
                    return;
                }
                wh.start();
                rh.start();
            } catch (ExecutionException e) {
                log.throwing("ConnectHandler", "completed", e);
                // ignore
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

        public void completed(IoFuture<Integer, Integer> result) {
            try {
                int rc = result.getNow();
                log.log(Level.FINEST, "Read {0} bytes", rc);
                if (rc < 0) {
                    log.warning("Read bailed with -1");
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
                    try {
                        channel.close();
                    } catch (IOException ignore) { }
                    disconnected();
                    return;
                }
                buf.clear();
                log.finest("Reading");
                channel.read(buf, opsRemaining - 1, this);
            } catch (ExecutionException e) {
                log.throwing("ReadHandler", "completed", e);
                disconnected();
                // ignore
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
                // ignore
            }
        }
    }

    void disconnected() {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE,
                "Disconnected {0} read:{1} wrote:{2}",
                new Object[] { channel, bytesRead, bytesWritten });
        }
        totalBytesRead.addAndGet(bytesRead);
        totalBytesWritten.addAndGet(bytesWritten);
        try {
            barrier.await();
        } catch (InterruptedException e) {
            log.warning("Barrier interrupted");
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException e) {
            log.warning("Barrier broken");
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

        barrier = new CyclicBarrier(NUM_CLIENTS + 1,
            new Runnable() {
                public void run() {
                    if (startTime == 0) {
                        log.info("Starting test");
                        startTime = System.nanoTime();
                        return;
                    }
                    long ops = NUM_CLIENTS * NUM_MSGS * 2;
                    long elapsed = System.nanoTime() - startTime;
                    log.log(Level.INFO, "Bytes read: {0}  written:{1}",
                        new Object[] {
                            totalBytesRead.get(),
                            totalBytesWritten.get()
                        });
                    log.log(Level.INFO, "{0} ops in {1} seconds = {2} ops/sec",
                        new Object[] {
                            ops,
                            TimeUnit.NANOSECONDS.toSeconds(elapsed),
                            TimeUnit.SECONDS.toNanos(ops) / elapsed
                        });
                }
            });

        int numThreads = NUM_CLIENTS * 2;

        log.log(Level.INFO,
            "Prestarting {0,number,integer} threads", numThreads);

        executor.setCorePoolSize(numThreads);
        executor.prestartAllCoreThreads();

        log.log(Level.INFO,
            "Connecting {0,number,integer} clients", NUM_CLIENTS);

        for (int i = 0; i < NUM_CLIENTS; ++i) {
            EchoClient client = new EchoClient(group);
            client.start();
        }

        barrier.await();
        barrier.await();

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
