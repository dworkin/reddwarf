/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.threaded;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.AsynchronousChannel;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

class AsyncChannelGroupImpl
    extends AsynchronousChannelGroup
    implements Executor
{
    /* Based on the Sun JDK ThreadPoolExecutor implementation. */

    /**
     * The executor service for this group's tasks.
     */
    final ExecutorService executor;

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

    void addChannel(AsynchronousChannel channel) {
        mainLock.lock();
        try {
            if (isShutdown())
                throw new ShutdownChannelGroupException();
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
            --groupSize;
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /* Termination support. */

    private void tryTerminate() {
        if (groupSize == 0) {
            int state = runState;
            if (state == STOP || state == SHUTDOWN) {
                runState = TERMINATED;
                termination.signalAll();
            }
        }
    }

    public void execute(Runnable command) {
        executor.execute(command);
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
