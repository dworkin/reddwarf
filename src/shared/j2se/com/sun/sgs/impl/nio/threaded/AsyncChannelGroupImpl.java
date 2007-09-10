/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.threaded;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.AsynchronousChannel;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

class AsyncChannelGroupImpl
    extends AsynchronousChannelGroup
{
    /**
     * The executor service for this group's tasks.
     */
    final ExecutorService executor;

    /**
     * Based on the Sun JDK ThreadPoolExecutor implementation.
     * <p>
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

    /**
     * Set containing all channels in group. Accessed only when
     * holding mainLock.
     */
    private final HashSet<AsynchronousChannel> channels =
        new HashSet<AsynchronousChannel>();

    /**
     * Current channel count, updated only while holding mainLock but
     * volatile to allow concurrent readability even during updates.
     */
    private volatile int groupSize = 0;

    /**
     * Current channel count, updated only while holding mainLock but
     * volatile to allow concurrent readability even during updates.
     */
    private volatile int pendingTaskCount = 0;

    /**
     * Creates a new AsyncChannelGroupImpl with the given provider and
     * executor service.
     *
     * @param provider the provider
     * @param executor the executor
     */
    AsyncChannelGroupImpl(ThreadedAsyncChannelProvider provider,
                          ExecutorService executor)
    {
        super(provider);
        if (executor == null)
            throw new NullPointerException("null ExecutorService");
        this.executor = executor;
    }

    /**
     * Throws ShutdownChannelGroupException if this group isShutdown().
     */
    private void checkShutdown() {
        if (isShutdown())
            throw new ShutdownChannelGroupException();
    }

    void addChannel(AsynchronousChannel channel) {
        mainLock.lock();
        try {
            checkShutdown();
            channels.add(channel);
            ++groupSize;
        } finally {
            mainLock.unlock();
        }
    }

    void channelClosed(AsynchronousChannel channel) {
        mainLock.lock();
        try {
            channels.remove(channel);
            if (--groupSize == 0)
                tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /* Termination support. */

    private void tryTerminate() {
        if (pendingTaskCount == 0) {
            int state = runState;
            if (state < STOP && groupSize > 0) {
                return;
            }
            if (state == STOP || state == SHUTDOWN) {
                runState = TERMINATED;
                termination.signalAll();
            }
        }
    }

    /**
     * Submits the callable to the thread pool for asynchronous execution,
     * returning an IoFuture of the appropriate type initialized with the
     * given attachment.  If handler is not null, it is invoked upon the
     * completion of the callable's execution with a distinct (completed)
     * IoFuture initialized with the provided attachment.
     *
     * @param <R> the result type
     * @param <A> the attachment type
     * @param callable the operation to perform asynchronously
     * @param attachment the attachment, or null
     * @param handler the completion handler, or null
     * @return an IoFuture
     */
    <R, A> IoFuture<R, A> submit(Callable<R> callable,
                                 A attachment,
                                 CompletionHandler<R, ? super A> handler)
    {
        mainLock.lock();
        try {
            ++pendingTaskCount;
        } finally {
            mainLock.unlock();
        }
        boolean success = false;
        try {
            AsyncIoTask<R, A> task =
                AsyncIoTask.create(callable, attachment, wrapHandler(handler));

            executor.execute(task);
            success = true;
            return task;
        } finally {
            if (! success)
                taskDone();
        }
    }

    void taskDone() {
        mainLock.lock();
        try {
            if (--pendingTaskCount == 0)
                tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    private <R, A> InnerHandler<R, A> wrapHandler(
            CompletionHandler<R, A> handler)
    {
        return new InnerHandler<R, A>(handler);
    }

    final class InnerHandler<R, A> implements CompletionHandler<R, A>
    {
        private final CompletionHandler<R, A> handler;

        InnerHandler(CompletionHandler<R, A> handler) {
            this.handler = handler;
        }
        
        public void completed(final IoFuture<R, A> result) {
            try {
                if (handler != null) {
                    executor.execute(new Runnable() {
                        public void run() {
                            handler.completed(result);
                        }
                    });
                }
            } finally {
                taskDone();
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
    public AsyncChannelGroupImpl shutdown() {
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
    public AsyncChannelGroupImpl shutdownNow() throws IOException
    {
        mainLock.lock();
        try {
            int state = runState;
            if (state < STOP)
                runState = STOP;

            for (AsynchronousChannel channel : channels)
                closeChannelNow(channel);

            tryTerminate();
            return this;
        } finally {
            mainLock.unlock();
        }
    }

    private void closeChannelNow(AsynchronousChannel channel) {
        try {
            channel.close();
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
}
