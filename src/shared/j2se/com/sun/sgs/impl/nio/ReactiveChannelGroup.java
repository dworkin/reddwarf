/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

/**
 * A select-based AsynchronousChannelGroup.
 */
class ReactiveChannelGroup
    extends AsyncGroupImpl
{
    static final Logger log =
        Logger.getLogger(ReactiveChannelGroup.class.getName());

    /**
     * The lifecycle state of this group.
     * Increases monotonically.
     */
    protected volatile int lifecycleState;
    protected static final int RUNNING      = 0;
    protected static final int SHUTDOWN     = 1;
    protected static final int SHUTDOWN_NOW = 2;
    protected static final int DONE         = 3;

    /** Lock held on updates to runState. */
    final ReentrantLock mainLock = new ReentrantLock();

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
            reactors.add(new Reactor(this, executor()));
        }

        for (Reactor reactor : reactors) {
            executor.execute(new WorkerStrategy(reactor));
        }
    }

    AsyncKey register(SelectableChannel ch) throws IOException {
        ch.configureBlocking(false);
        AsyncKey asyncKey = null;
        @SuppressWarnings("hiding")
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (lifecycleState != RUNNING)
                throw new ShutdownChannelGroupException();

            int k = reactorBucketStrategy(ch);
            Reactor reactor = reactors.get(Math.abs(k % reactors.size()));
            asyncKey = reactor.register(ch);
            return asyncKey;
        } finally {
            mainLock.unlock();
            if (asyncKey == null) {
                try {
                    ch.close();
                } catch (Throwable t) {
                    try {
                        log.log(Level.FINE, "exception closing" + ch, t);
                    } catch (Throwable ignore) {}
                }
            }
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
                final ReentrantLock mainLock =
                    ReactiveChannelGroup.this.mainLock;
                mainLock.lock();
                try {
                    log.log(Level.FINER, "handling reactor shutdown");
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
                if (lifecycleState == DONE   )
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
        return lifecycleState != RUNNING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return lifecycleState == DONE   ;
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
            if (lifecycleState < SHUTDOWN)
                lifecycleState = SHUTDOWN;

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
            if (lifecycleState < SHUTDOWN_NOW)
                lifecycleState = SHUTDOWN_NOW;

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
        if (lifecycleState == RUNNING || lifecycleState == DONE)
            return;

        if (reactors.isEmpty()) {
            lifecycleState = DONE;
            termination.signalAll();
        }
    }

}
