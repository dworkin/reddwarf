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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.AbortedByTimeoutException;
import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;
import com.sun.sgs.nio.channels.WritePendingException;

class ReactiveChannelGroup
    extends AbstractAsyncChannelGroup
    implements Runnable
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
    
    volatile boolean selecting = false;

    /**
     * Lock held on updates to runState and channel set.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    final Selector selector;
    final DelayQueue<AsyncOp<?>> timeouts =
        new DelayQueue<AsyncOp<?>>();

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
        executor().execute(this);
    }

    BlockingQueue<Runnable> tasks =
        new LinkedBlockingQueue<Runnable>();

    @Override
    void registerChannel(final SelectableChannel channel) throws IOException {
        mainLock.lock();
        try {
            if (isShutdown()) {
                try {
                    channel.close();
                } catch (IOException ignore) { }
                throw new ShutdownChannelGroupException();
            }
            channel.configureBlocking(false);
            FutureTask<Void> task = new FutureTask<Void>(
                new Callable<Void>() {
                    public Void call() throws IOException {
                        channel.register(selector, 0, new KeyDispatcher(channel));
                        return null;
                    }
                });
            if (! selecting) {
                task.run();
            } else {
                tasks.add(task);
                selector.wakeup();
            }
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException)
                    throw (IOException) cause;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            }
        } finally {
            mainLock.unlock();
        }
    }

    final class KeyDispatcher implements Channel {
        private final SelectableChannel channel;

        private volatile AsyncOp<?> acceptTask = null;
        private volatile AsyncOp<?> connectTask = null;
        private volatile AsyncOp<?> readTask = null;
        private volatile AsyncOp<?> writeTask = null;

        KeyDispatcher(SelectableChannel channel) {
            this.channel = channel;
        }
        
        synchronized void add(AsyncOp<?> op) {
            SelectionKey key = channel.keyFor(selector);
            switch (op.getOp()) {
            case OP_ACCEPT:
                if (acceptTask != null)
                    throw new AcceptPendingException();
                key.interestOps(key.interestOps() | OP_ACCEPT);
                acceptTask = op;
                break;
            case OP_CONNECT:
                if (connectTask != null)
                    throw new ConnectionPendingException();
                key.interestOps(key.interestOps() | OP_CONNECT);
                connectTask = op;
                break;
            case OP_READ:
                if (readTask != null)
                    throw new ReadPendingException();
                key.interestOps(key.interestOps() | OP_READ);
                readTask = op;
                break;
            case OP_WRITE:
                if (writeTask != null)
                    throw new WritePendingException();
                key.interestOps(key.interestOps() | OP_WRITE);
                writeTask = op;
                break;
            }
        }
        
        synchronized void setException(int op, Throwable t) {
            SelectionKey key = channel.keyFor(selector);
            switch (op) {
            case OP_ACCEPT:
                if (acceptTask != null) {
                    key.interestOps(key.interestOps() & (~ OP_ACCEPT));
                    acceptTask.setException(t);
                    acceptTask = null;
                }
                break;
            case OP_CONNECT:
                if (connectTask != null) {
                    key.interestOps(key.interestOps() & (~ OP_CONNECT));
                    connectTask.setException(t);
                    connectTask = null;
                }
                break;
            case OP_READ:
                if (readTask != null) {
                    key.interestOps(key.interestOps() & (~ OP_READ));
                    readTask.setException(t);
                    readTask = null;
                }
                break;
            case OP_WRITE:
                if (writeTask != null) {
                    key.interestOps(key.interestOps() & (~ OP_WRITE));
                    writeTask.setException(t);
                    writeTask = null;
                }
                break;
            }
        }

        public synchronized void close() throws IOException {
            channel.close();

            if (acceptTask != null) {
                acceptTask.setException(new AsynchronousCloseException());
                acceptTask = null;
            }
            if (connectTask != null) {
                connectTask.setException(new AsynchronousCloseException());
                connectTask = null;
            }
            if (readTask != null) {
                readTask.setException(new AsynchronousCloseException());
                readTask = null;
            }
            if (writeTask != null) {
                writeTask.setException(new AsynchronousCloseException());
                writeTask = null;
            }
        }

        synchronized void selected(int readyOps) throws IOException {
            if ((readyOps & OP_ACCEPT) != 0 && acceptTask != null) {
                acceptTask.run();
                acceptTask = null;
            }
            if ((readyOps & OP_CONNECT) != 0 && connectTask != null) {
                connectTask.run();
                connectTask = null;
            }
            if ((readyOps & OP_READ) != 0 && readTask != null) {
                readTask.run();
                readTask = null;
            }
            if ((readyOps & OP_WRITE) != 0 && writeTask != null) {
                writeTask.run();
                writeTask = null;
            }
        }

        public boolean isOpen() {
            return channel.isOpen();
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
                selector.wakeup();
            } catch (IOException ignore) { }
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    int doSelect() throws IOException {
        selecting = true;
        try {
            return selector.select(getSelectorTimeout());
        } finally {
            selecting = false;
        }
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

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                int readyOps = key.readyOps();
                key.interestOps(key.interestOps() & (~ readyOps));
                try {
                    getDispatcher(key).selected(readyOps);
                } catch (IOException e) {
                    // TODO
                }
            }

            List<AsyncOp<?>> expired = new ArrayList<AsyncOp<?>>();
            timeouts.drainTo(expired);

            for (AsyncOp<?> op : expired) {
                getDispatcher(op.getChannel()).setException(op.getOp(),
                    new AbortedByTimeoutException());
            }

            List<Runnable> ts = new ArrayList<Runnable>();
            tasks.drainTo(ts);
            for (Runnable r : ts)
                r.run();

            executor().execute(this);
            
        } catch (Exception e) {
            // TODO
        }
    }
    
    @Override
    boolean isOpPending(SelectableChannel channel, int op) {
        try {
            return (channel.keyFor(selector).interestOps() & op) != 0;
        } catch (RuntimeException e) {
            return false;
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
                forceClose(getDispatcher(key));

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

    @Override
    void execute(AsyncOp<?> op) {
        mainLock.lock();
        try {
            SelectableChannel channel = op.getChannel();
            if (! channel.isOpen())
                throw new ClosedAsynchronousChannelException();
            long timeout = op.getDelay(TimeUnit.MILLISECONDS);
            if (timeout < 0)
                throw new IllegalArgumentException("Negative timeout");
            if (timeout > 0)
                timeouts.add(op);
            getDispatcher(channel).add(op);
            selector.wakeup();
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    <R, A> IoFuture<R, A> submit(SelectableChannel channel, int op, A attachment, CompletionHandler<R, ? super A> handler, Callable<R> callable)
    {
        return submit(channel, op, 0, TimeUnit.MILLISECONDS,
                      attachment, handler, callable);
    }

    @Override
    <R, A> IoFuture<R, A> submit(SelectableChannel channel, int op, long timeout, TimeUnit unit, A attachment, CompletionHandler<R, ? super A> handler, Callable<R> callable)
    {
        AsyncOp<R> asyncOp =
            AsyncOp.create(executor(), channel, op, timeout, unit,
                           attachment, handler, callable);
        execute(asyncOp);
        return AttachedFuture.wrap(asyncOp, attachment);
    }
}
