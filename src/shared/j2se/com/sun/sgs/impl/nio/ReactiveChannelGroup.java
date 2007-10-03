/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

/**
 * A select-based AsynchronousChannelGroup.
 */
class ReactiveChannelGroup
    extends AsyncGroupImpl
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

    /** Lock held on updates to runState. */
    protected final ReentrantLock mainLock = new ReentrantLock();

    /** Termination condition. */
    private final Condition termination = mainLock.newCondition();

    /**
     * The property to specify the number of reactors to be used by
     * channel groups: {@value}
     */
    public static final String REACTORS_PROPERTY =
        "com.sun.sgs.nio.async.reactive.selectors";

    /**
     * The default number of reactors to be used by channel groups: {@value}
     */
    public static final int DEFAULT_REACTORS = 1;

    final List<Reactor> reactors;

    ReactiveChannelGroup(ReactiveAsyncChannelProvider provider,
                         ExecutorService executor)
        throws IOException
    {
        this(provider, executor, 0);
    }

    // if requestedReactors == 0, choose from a property
    ReactiveChannelGroup(ReactiveAsyncChannelProvider provider,
                         ExecutorService executor,
                         int requestedReactors)
        throws IOException
    {
        super(provider, executor);

        int n = requestedReactors;

        if (n == 0) {
            try {
                n = Integer.valueOf(System.getProperty(REACTORS_PROPERTY));
            } catch (NumberFormatException e) {
                n = DEFAULT_REACTORS;
            }
        }

        if (n <= 0) {
            throw new IllegalArgumentException("non-positive reactor count");
        }

        reactors = new ArrayList<Reactor>(n);

        for (int i = 0; i < n; ++i) {
            Selector sel = selectorProvider().openSelector();
            reactors.add(new Reactor(sel));
        }

        for (Reactor reactor : reactors) {
            executor.execute(new WorkerStrategy(reactor));
        }
    }

    <T extends SelectableChannel> AsyncKey<T>
    register(T ch) throws IOException {
        ch.configureBlocking(false);
        AsyncKey<T> asyncKey = null;
        @SuppressWarnings("hiding")
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (runState != RUNNING)
                throw new ShutdownChannelGroupException();

            int k = reactorBucketStrategy(ch);
            Reactor reactor = reactors.get(Math.abs(k % reactors.size()));
            asyncKey = reactor.register(ch);
            return asyncKey;
        } finally {
            mainLock.unlock();
            if (asyncKey == null)
                Util.forceClose(ch);
        }
    }

    /**
     * Returns a reactor bucket for the given channel.
     *
     * @param ch a channel
     * @return the reactor bucket for the channel
     */
    protected int reactorBucketStrategy(SelectableChannel ch) {
        return ch.hashCode();
    }
    
    class WorkerStrategy implements Runnable {
        private final Reactor reactor;

        WorkerStrategy(Reactor reactor) {
            this.reactor = reactor;
        }

        /** {@inheritDoc} */
        public void run() {
            try {
                while (reactor.run()) { /* empty */ }
            } finally {
                @SuppressWarnings("hiding")
                ReentrantLock mainLock = ReactiveChannelGroup.this.mainLock;
                mainLock.lock();
                try {
                    reactors.remove(reactor);
                    tryTerminate();
                } finally {
                    mainLock.unlock();
                }
            }
        }
    }

    /* Termination support. */

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException
    {
        long nanos = unit.toNanos(timeout);
        @SuppressWarnings("hiding")
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
    public ReactiveChannelGroup shutdown() {
        @SuppressWarnings("hiding")
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int state = runState;
            if (state < SHUTDOWN)
                runState = SHUTDOWN;

            for (Reactor reactor : reactors)
                reactor.shutdown();

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
    public ReactiveChannelGroup shutdownNow() throws IOException {
        @SuppressWarnings("hiding")
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int state = runState;
            if (state < STOP)
                runState = STOP;

            for (Reactor reactor : reactors) {
                try {
                    reactor.shutdownNow();
                } catch (Throwable ignore) { }
            }

            tryTerminate();

            return this;
        } finally {
            mainLock.unlock();
        }
    }

    private void tryTerminate() {
        if (runState == RUNNING || runState == TERMINATED)
            return;

        if (reactors.isEmpty())
            termination.signalAll();
    }

    /**
     * Invokes {@link AsynchronousChannelGroup#shutdown()} when this
     * group is no longer referenced.
     */
    @Override
    protected void finalize() {
        shutdown();
    }

}
