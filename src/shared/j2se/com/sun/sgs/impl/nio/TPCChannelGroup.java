/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

class TPCChannelGroup
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

    /**
     * Set containing all channels in group. Accessed only when
     * holding mainLock.
     */
    private final Set<AsyncChannelImpl> channels =
        new HashSet<AsyncChannelImpl>();

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
    TPCChannelGroup(AsyncProviderImpl provider, ExecutorService executor) {
        super(provider, executor);
    }
    @Override
    void registerChannel(AsyncChannelImpl ach) throws IOException {
        mainLock.lock();
        try {
            if (isShutdown()) {
                forceClose(ach);
                throw new ShutdownChannelGroupException();
            }
            channels.add(ach);
            ++groupSize;
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    void unregisterChannel(AsyncChannelImpl ach) {
        mainLock.lock();
        try {
            channels.remove(ach);
            --groupSize;
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
        
    }

    @Override
    void awaitReady(AsyncChannelImpl ach, int op) {
        awaitReady(ach, op, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    void awaitReady(final AsyncChannelImpl ach,
                    final int op,
                    long timeout,
                    TimeUnit unit)
    {
        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");
        if (timeout > 0) {
            // TODO
            throw new UnsupportedOperationException("timeout not implemented");
        }
        ach.selected(op);
    }

    void awaitSelectableOp(SelectableChannel channel, long timeout, int ops)
        throws IOException
    {
        if (timeout == 0)
            return;
/*
        Selector sel = getSelectorProvider().openSelector();
        channel.register(sel, ops);
        if (sel.select(timeout) == 0)
            throw new AbortedByTimeoutException();
*/
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
    public TPCChannelGroup shutdown() {
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
    public TPCChannelGroup shutdownNow() throws IOException
    {
        mainLock.lock();
        try {
            int state = runState;
            if (state < STOP)
                runState = STOP;

            for (AsyncChannelImpl channel : channels) {
                try {
                    channel.close();
                } catch (IOException ignore) { }
            }

            tryTerminate();
            return this;
        } finally {
            mainLock.unlock();
        }
    }

    private static void forceClose(AsyncChannelImpl channel) {
    }
}
