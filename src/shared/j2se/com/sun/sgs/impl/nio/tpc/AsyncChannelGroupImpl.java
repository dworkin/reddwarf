/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.tpc;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.AsynchronousChannel;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

/*
 * For the moment, this borrows a lot of code and structure from
 * ThreadPoolExecutor; however, this is expected to change.
 */

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

    private final ConcurrentHashMap<AsynchronousChannel, Boolean> channels;

    AsyncChannelGroupImpl(ThreadedAsyncChannelProvider provider,
                          ExecutorService executor) 
        throws IOException
    {
        super(provider);
        if (executor == null)
            throw new NullPointerException("null ExecutorService");
        this.executor = executor;
        channels = new ConcurrentHashMap<AsynchronousChannel, Boolean>();
    }

    AsyncChannelGroupImpl checkShutdown() {
        if (isShutdown())
            throw new ShutdownChannelGroupException();
        return this;
    }

    void register(AsynchronousChannel channel) {
        if (channels.putIfAbsent(channel, true) != null) {
            // TODO error
        }
    }

    void unregister(AsynchronousChannel channel) {
        if (channels.remove(channel) == null) {
            // TODO error? ignore?
        }
    }
    
    ExecutorService getExecutor() {
        return executor;
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
    public boolean isShutdown()
    {
        return runState != RUNNING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated()
    {
        return runState == TERMINATED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncChannelGroupImpl shutdown()
    {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int state = runState;
            if (state < SHUTDOWN)
                runState = SHUTDOWN;

            // TODO wait for connections to empty
            // TODO notify waiters
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
            // TODO notify waiters
            return this;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes <tt>shutdown</tt> when this channel group is no longer
     * referenced.
     */
    @Override
    protected void finalize()  {
        shutdown();
    }
}
