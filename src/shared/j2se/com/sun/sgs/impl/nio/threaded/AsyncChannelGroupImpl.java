/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.threaded;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

class AsyncChannelGroupImpl
    extends AsynchronousChannelGroup
{
    final ExecutorService executor;

    volatile int runState;
    static final int RUNNING    = 0;
    static final int SHUTDOWN   = 1;
    static final int STOP       = 2;
    static final int TERMINATED = 3;

    private final ReentrantLock mainLock = new ReentrantLock();
    private final Condition termination = mainLock.newCondition();

    AsyncChannelGroupImpl(ThreadedAsyncChannelProvider provider,
                          ExecutorService executor) 
        throws IOException
    {
        super(provider);
        if (executor == null)
            throw new NullPointerException("null ExecutorService");
        this.executor = executor;
    }

    AsyncChannelGroupImpl checkShutdown() {
        if (isShutdown())
            throw new ShutdownChannelGroupException();
        return this;
    }

    <R, A> IoFuture<R, A> submit(Callable<R> callable,
                                 A attachment,
                                 CompletionHandler<R, ? super A> handler)
    {
        AsyncIoTask<R, A> task =
            AsyncIoTask.create(callable, attachment, wrapHandler(handler));
        incrementTaskCount();
        boolean success = false;
        try {
            executor.execute(task);
            success = true;
            return task;
        } finally {
            if (! success)
                decrementTaskCount();
        }
    }

    final AtomicInteger pendingTasks = new AtomicInteger();

    private void incrementTaskCount() {
        pendingTasks.incrementAndGet();
    }

    private void decrementTaskCount() {
        pendingTasks.decrementAndGet();
    }

    // MUST BE CALLED WITH mainLock HELD!
    private void checkTermination() {
        if ((runState != RUNNING) && (0 == pendingTasks.get()))
            termination.signalAll();
    }

    private <R, A> InnerHandler<R, A> wrapHandler(
            CompletionHandler<R, A> handler)
    {
        return new InnerHandler<R, A>(handler);
    }

    final class InnerHandler<R, A> implements CompletionHandler<R, A> {
        private final CompletionHandler<R, A> handler;

        InnerHandler(CompletionHandler<R, A> handler) {
            this.handler = handler;
        }

        public void completed(IoFuture<R, A> result) {
            decrementTaskCount();
            handler.completed(result);
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
        final ReentrantLock mainLock = this.mainLock;
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
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int state = runState;
            if (state < SHUTDOWN)
                runState = SHUTDOWN;

            checkTermination();
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
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int state = runState;
            if (state < STOP)
                runState = STOP;

            // TODO close all connections

            checkTermination();
            return this;
        } finally {
            mainLock.unlock();
        }
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
