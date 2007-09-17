/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

class ReactiveChannelGroup
    extends AbstractAsyncChannelGroup
{
    /* Based on the Sun JDK ThreadPoolExecutor implementation. */

    /**
     * runState provides the main lifecyle control, taking on values:
     *
     *   RUNNING:  Accept new tasks and process queued tasks
     *   SHUTDOWN: Don't accept new tasks, but process queued tasks
     *   STOP:     Don't accept new tasks, don't process queued tasks,
     *             and interrupt in-progress tasks
     *   TERMINATED: Same as STOP, plus all threads have terminated
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * RUNNING -> SHUTDOWN
     *    On invocation of shutdown(), perhaps implicitly in finalize()
     * (RUNNING or SHUTDOWN) -> STOP
     *    On invocation of shutdownNow()
     * SHUTDOWN -> TERMINATED
     *    When both queue and group are empty
     * STOP -> TERMINATED
     *    When group is empty
     */
    volatile int runState;
    static final int RUNNING    = 0;
    static final int SHUTDOWN   = 1;
    static final int STOP       = 2;
    static final int TERMINATED = 3;

    /**
     * Lock held on updates to runState and channel set.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    abstract class DelayedRunnable implements Runnable, Delayed {
        final long timeout;
        final TimeUnit unit;

        DelayedRunnable(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.unit = unit;
        }

        public long getDelay(TimeUnit unit) {
            return unit.convert(timeout, unit);
        }

        public int compareTo(Delayed o) {
            final long other = o.getDelay(unit);
            return (timeout<other ? -1 : (timeout==other ? 0 : 1));
        }
    }

    final Selector selector;
    final DelayQueue<DelayedRunnable> timeouts =
        new DelayQueue<DelayedRunnable>();

    /**
     * Creates a new AsyncChannelGroupImpl with the given provider and
     * executor service.
     *
     * @param provider the provider
     * @param executor the executor
     */
    ReactiveChannelGroup(AsyncProviderImpl provider, ExecutorService executor)
        throws IOException
    {
        super(provider, executor);
        selector = selectorProvider().openSelector();
    }

    @Override
    void registerChannel(SelectableChannel channel) throws IOException {
        mainLock.lock();
        try {
            if (isShutdown()) {
                try {
                    channel.close();
                } catch (IOException ignore) { }
                throw new ShutdownChannelGroupException();
            }
            channel.register(selector, 0, new KeyDispatcher(channel));
        } finally {
            mainLock.unlock();
        }
    }

    final class KeyDispatcher {
        private final SelectableChannel channel;

        private volatile FutureTask<?> acceptFuture = null;
        private volatile FutureTask<?> connectFuture = null;
        private volatile FutureTask<?> readFuture = null;
        private volatile FutureTask<?> writeFuture = null;

        KeyDispatcher(SelectableChannel channel) {
            this.channel = channel;
        }

        void close() throws IOException {
            if (acceptFuture != null) {
                acceptFuture.cancel(true);
                acceptFuture = null;
            }
            if (connectFuture != null) {
                connectFuture.cancel(true);
                connectFuture = null;
            }
            if (readFuture != null) {
                readFuture.cancel(true);
                readFuture = null;
            }
            if (writeFuture != null) {
                writeFuture.cancel(true);
                writeFuture = null;
            }
            channel.close();
        }

        void selected(SelectionKey key) throws IOException {
            if (key.isAcceptable() && acceptFuture != null) {
                acceptFuture.run();
                acceptFuture = null;
            }
            if (key.isConnectable() && connectFuture != null) {
                connectFuture.run();
                connectFuture = null;
            }
            if (key.isReadable() && readFuture != null) {
                readFuture.run();
                readFuture = null;
            }
            if (key.isWritable() && writeFuture != null) {
                writeFuture.run();
                writeFuture = null;
            }
        }
    }

    KeyDispatcher getDispatcher(SelectableChannel channel) {
        return getDispatcher(channel.keyFor(selector));
    }

    KeyDispatcher getDispatcher(SelectionKey key) {
        return (KeyDispatcher) key.attachment();
    }

    void closeChannel(SelectableChannel channel) {
        mainLock.lock();
        try {
            try {
                getDispatcher(channel).close();
            } catch (IOException ignore) { }
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    class ReactiveAsyncOp<T extends SelectableChannel> extends AsyncOp<T> {

        private final SelectionKey key;
        private RunnableFuture<?> acceptTask;
        private RunnableFuture<?> connectTask;
        private RunnableFuture<?> readTask;
        private RunnableFuture<?> writeTask;

        ReactiveAsyncOp(T channel) throws IOException {
            key = channel.register(selector, 0, this);
        }

        public void close() throws IOException {
            try {
                super.close();
            } finally {
                channelClosed(this);
            }
        }

        @SuppressWarnings("unchecked")
        T channel() {
            // Safe to cast because a T was used to create the key
            return (T) key.channel();
        }

        ReactiveChannelGroup group() {
            return ReactiveChannelGroup.this;
        }

        boolean isPending(int op) {
            return (key.interestOps() & op) != 0;
        }

        void setOp(int op) {
            synchronized (this) {
                checkPending(op);
                key.interestOps(key.interestOps() | op);
                selector.wakeup();
            }
        }

        void clearOp(int op) {
            synchronized (this) {
                key.interestOps(key.interestOps() & ~op);
                selector.wakeup();
            }
        }

        <R, A> IoFuture<R, A>
        submit(int op,
               A attachment,
               CompletionHandler<R, ? super A> handler, 
               long timeout, 
               TimeUnit unit, 
               Callable<R> callable)
        {
            if (timeout > 0) {
                timeouts.add(new DelayedRunnable(timeout, unit) {
                    public void run() {
                        // set timeout exception on future
                    }
                });
            }
            return null;
        }
    }

    int doSelect() throws IOException {
        return selector.select(getSelectorTimeout());
    }

    int getSelectorTimeout() {
        final Delayed t = timeouts.peek();
        return (t == null) ? 0 : (int) t.getDelay(TimeUnit.MILLISECONDS);
    }

    public void run() {
        try {
            int rc = doSelect();
            
            if (! selector.isOpen()) {
                // TODO
                return;
            }

            List<DelayedRunnable> expired = new ArrayList<DelayedRunnable>();
            timeouts.drainTo(expired);

            for (Runnable r : expired)
                r.run();

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                int readyOps = key.readyOps();
                ReactiveAsyncOp<? extends SelectableChannel> op = getOp(key);
                keys.remove();
                op.ready(readyOps);
            }
            
        } catch (Exception e) {
            // TODO
        }
    }

    /* Termination support. */

    private void tryTerminate() {
        if (selector.keys().isEmpty()) {
            int state = runState;
            if (state == STOP || state == SHUTDOWN) {
                runState = TERMINATED;
                termination.signalAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException
    {
        long nanos = unit.toNanos(timeout);
        mainLock.lock();
        try {
            for (;;) {
                if (runState == TERMINATED)
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return runState != RUNNING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return runState == TERMINATED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactiveChannelGroup shutdown() {
        mainLock.lock();
        try {
            int state = runState;
            if (state < SHUTDOWN)
                runState = SHUTDOWN;

            tryTerminate();
            return this;
        } finally {
            mainLock.unlock();
        }
    }

    ReactiveAsyncOp<? extends SelectableChannel> getOp(SelectionKey key) {
        return (ReactiveAsyncOp<? extends SelectableChannel>) key.attachment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactiveChannelGroup shutdownNow() throws IOException
    {
        mainLock.lock();
        try {
            int state = runState;
            if (state < STOP)
                runState = STOP;

            Set<SelectionKey> keys = selector.keys();
            selector.close();
            for (SelectionKey key : keys)
                forceClose(getOp(key));

            tryTerminate();
            return this;
        } finally {
            mainLock.unlock();
        }
    }

    private void forceClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignore) { }
    }

    /**
     * Invokes {@code shutdown} when this channel group is no longer
     * referenced.
     */
    @Override
    protected void finalize() {
        // TODO is this actually useful? -JM
        shutdown();
    }


    void selected(int ops) {
        if ((ops & OP_CONNECT) != 0 && connectTask != null) {
            connectTask.run();
        }
        if ((ops & OP_READ) != 0 && readTask != null) {
            readTask.run();
        }
        if ((ops & OP_WRITE) != 0 && writeTask != null) {
            writeTask.run();
        }
    }
}
