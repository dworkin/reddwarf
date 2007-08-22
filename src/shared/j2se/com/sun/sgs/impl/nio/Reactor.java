/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class Reactor
{
    public interface Handler {
        public void channelClosed(Reactor reactor, SelectableChannel channel);
        public boolean channelReady(Reactor reactor, SelectableChannel channel, int ops) throws IOException;
    }

    private final Selector selector;
    private final ExecutorService executor;

    private final ConcurrentLinkedQueue<Runnable> opQueue =
        new ConcurrentLinkedQueue<Runnable>();

    private volatile boolean wantShutdown = false;

    public Reactor()
        throws IOException
    {
        this(Executors.newSingleThreadExecutor());
    }

    public Reactor(ExecutorService executor)
        throws IOException
    {
        if (executor == null) {
            throw new NullPointerException("executor must not be null");
        }
        this.selector = Selector.open();
        this.executor = executor;

        this.executor.submit(new Worker());
    }

    final class RegisterChannelTask
        implements Callable<Void>
    {
        private final SelectableChannel channel;
        private final int ops;
        private final Object attachment;

        RegisterChannelTask(SelectableChannel channel, int ops, Object attachment) {
            this.channel = channel;
            this.ops = ops;
            this.attachment = attachment;
        }

        public Void call() throws ClosedChannelException {
            channel.register(selector, ops, attachment);
            return null;
        }
    }

    final class CloseChannelTask
        implements Callable<Void>
    {
        private final SelectableChannel channel;

        CloseChannelTask(SelectableChannel channel) {
            this.channel = channel;
        }

        public Void call() throws IOException {
            if (! channel.isOpen())
                return null;

            SelectionKey key = channel.keyFor(selector);
            if (key == null)
                return null;

            try {
                channel.close();
            } catch (IOException ignore) { }

            Handler handler = (Handler) key.attachment();
            try {
                handler.channelClosed(Reactor.this, key.channel());
            } catch (RuntimeException ignore) { }
            key.attach(null);
            return null;
        }
    }

    final class InterestOpsTask
        implements Callable<Void>
    {
        private final SelectableChannel channel;
        private final int setOps;
        private final int clearOps;

        InterestOpsTask(SelectableChannel channel, int setOps, int clearOps) {
            this.channel = channel;
            this.setOps = setOps;
            this.clearOps = clearOps;
        }

        public Void call() {
            SelectionKey key = channel.keyFor(selector);
            key.interestOps((key.interestOps() & (~clearOps)) | setOps);
            return null;
        }
    }

    public Future<Void> registerChannel(SelectableChannel channel, int ops, Handler handler) {
        FutureTask<Void> futureTask =
            new FutureTask<Void>(new RegisterChannelTask(channel, ops, handler));
        opQueue.add(futureTask);
        selector.wakeup();
        return futureTask;
    }

    /**
     * Asynchronously close this channel.
     * <p>
     * Note: this Reactor only generates channelClosed callbacks for
     * channels that are closed by this method or by the network layer. In
     * particular, calling close() on the underlying Socket or Channel
     * directly will <b>not</b> generate a channelClosed callback.
     */
    public Future<Void> closeChannel(SelectableChannel channel) {
        FutureTask<Void> futureTask =
            new FutureTask<Void>(new CloseChannelTask(channel));
        opQueue.add(futureTask);
        selector.wakeup();
        return futureTask;
    }

    public Future<Void> setInterestOps(SelectableChannel channel, int setOps) {
        return updateInterestOps(channel, setOps, 0);
    }

    public Future<Void> clearInterestOps(SelectableChannel channel, int clearOps) {
        return updateInterestOps(channel, 0, clearOps);
    }

    public Future<Void> updateInterestOps(SelectableChannel channel, int setOps, int clearOps) {
        FutureTask<Void> futureTask =
            new FutureTask<Void>(new InterestOpsTask(channel, setOps, clearOps));
        opQueue.add(futureTask);
        selector.wakeup();
        return futureTask;
    }

    public void shutdown() {
        wantShutdown = true;
        selector.wakeup();
    }

    public void shutdownNow() {
        try {
            selector.close();
        } catch (IOException ignore) { }
        executor.shutdownNow();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException
    {
        return executor.awaitTermination(timeout, unit);
    }

    static final int MAX_DISPATCHES_PER_WORK_LOOP = 1;

    final class Worker implements Callable<Void> {
        public Void call() throws IOException {
            try {
                if (! selector.isOpen()) {
                    return null;
                }

                // Process registration and interest-set operations
                Runnable op;
                while ((op = opQueue.poll()) != null) {
                    try {
                        op.run();
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                }

                // Already have pending events from a previous work loop?
                if (selector.selectedKeys().isEmpty()) {
                    // Wait for events
                    selector.select();
                }

                if (! selector.isOpen()) {
                    return null;
                }

                if (selector.keys().isEmpty() && wantShutdown) {
                    executor.shutdown();
                    selector.close();
                    return null;
                }

                // Handle events
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                int allowedDispatches = MAX_DISPATCHES_PER_WORK_LOOP;
                while (keys.hasNext() &&
                        (allowedDispatches-- > 0) &&
                        (! Thread.currentThread().isInterrupted()))
                {
                    SelectionKey key = keys.next();

                    Handler handler = (Handler) key.attachment();
                    boolean removeKey = true;
                    try {
                        removeKey = handler.channelReady(
                            Reactor.this, key.channel(), key.readyOps());
                    } catch (Exception e) {
                        closeChannel(key.channel());
                    } finally {
                        if (! key.isValid()) {
                            closeChannel(key.channel());
                        }
                        if (removeKey) {
                            keys.remove();
                        }
                    }
                }

                executor.submit(this);

                return null;

            } catch (IOException e) {
                try {
                    selector.close();
                } catch (IOException ignore) { }
                throw e;
            }
        }
    }
}
