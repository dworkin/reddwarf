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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.sgs.nio.channels.AbortedByTimeoutException;
import com.sun.sgs.nio.channels.AcceptPendingException;
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

    /**
     * Lock held on updates to runState and channel set.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Selector guard.  May be locked *after* mainLock, but not before.
     */
    private final Object selectorLock = new Object();

    final Selector selector;
    final DelayQueue<TimeoutHandler> timeouts =
        new DelayQueue<TimeoutHandler>();

    static class TimeoutHandler implements Delayed, Runnable {
        private final AsyncChannelImpl asyncChannel;
        private final int op;
        private final long timeout;
        private final TimeUnit timeoutUnit;

        TimeoutHandler(AsyncChannelImpl ach,
                       int op,
                       long timeout,
                       TimeUnit unit)
        {
            this.asyncChannel = ach;
            this.op = op;
            this.timeout = timeout;
            timeoutUnit = unit;
        }

        public long getDelay(TimeUnit unit) {
            return unit.convert(timeout, timeoutUnit);
        }

        public int compareTo(Delayed o) {
            final long other = o.getDelay(timeoutUnit);
            return (timeout<other ? -1 : (timeout==other ? 0 : 1));
        }

        public void run() {
            asyncChannel.setException(op, new AbortedByTimeoutException());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            TimeoutHandler other = (TimeoutHandler) obj;
            return asyncChannel == other.asyncChannel && op == other.op;
        }

        @Override
        public int hashCode() {
            return asyncChannel.hashCode() ^ (1 << op);
        }
    }

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
        execute(this);
    }

    @Override
    void registerChannel(AsyncChannelImpl ach) throws IOException {
        mainLock.lock();
        try {
            final SelectableChannel ch = ach.channel();
            if (isShutdown()) {
                try {
                    ch.close();
                } catch (IOException ignore) { }
                throw new ShutdownChannelGroupException();
            }
            ch.configureBlocking(false);
            synchronized (selectorLock) {
                selector.wakeup();
                ch.register(selector, 0, ach);
            }
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    void unregisterChannel(AsyncChannelImpl ach) {
        selector.wakeup();
    }

    @Override
    void awaitReady(AsyncChannelImpl ach, int op) {
        awaitReady(ach, op, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    void awaitReady(AsyncChannelImpl ach, int op, long timeout, TimeUnit unit) {
        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");
        if (timeout > 0) {
            // TODO
            throw new UnsupportedOperationException("timeout not implemented");
        }
        synchronized (selectorLock) {
            selector.wakeup();
            SelectionKey key = ach.channel().keyFor(selector);
            int interestOps = key.interestOps();
            checkPending(interestOps, op);
            key.interestOps(interestOps | op);
        }
    }

    private void checkPending(int interestOps, int op) {
        if ((interestOps & op) == 0)
            return;

        switch (op) {
        case OP_ACCEPT:
            throw new AcceptPendingException();
        case OP_CONNECT:
            throw new ConnectionPendingException();
        case OP_READ:
            throw new ReadPendingException();
        case OP_WRITE:
            throw new WritePendingException();
        default:
            throw new IllegalStateException("Unexpected op " + op);
        }
    }

    protected int doSelect() throws IOException {
        return selector.select(getSelectorTimeout());
    }

    protected int getSelectorTimeout() {
        final Delayed t = timeouts.peek();
        return (t == null) ? 0 : (int) t.getDelay(TimeUnit.MILLISECONDS);
    }

    public void run() {
        try {
            // Obtain and release the guard to allow other tasks to run
            // after waking the selector.
            synchronized (selectorLock) { /* empty */ }

            doSelect();

            if (runState != RUNNING) {
                mainLock.lock();
                try {
                    if (runState == TERMINATED)
                        return;

                    selector.selectNow(); // clear cancelled keys
                    if (selector.keys().isEmpty()) {
                        int state = runState;
                        if (state == STOP || state == SHUTDOWN) {
                            runState = TERMINATED;
                            try {
                                selector.close();
                            } catch (IOException ignore) { }
                            termination.signalAll();
                            return;
                        }
                    }
                } finally {
                    mainLock.unlock();
                }
            }

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                int readyOps = key.readyOps();
                key.interestOps(key.interestOps() & (~ readyOps));
                AsyncChannelImpl ach = (AsyncChannelImpl) key.attachment();
                ach.selected(readyOps);
            }

            if (timeouts.peek() != null) {
                List<TimeoutHandler> expiredHandlers = new ArrayList<TimeoutHandler>();
                timeouts.drainTo(expiredHandlers);

                for (TimeoutHandler expired : expiredHandlers)
                    expired.run();
            }

            execute(this);
            
        } catch (Throwable t) {
            // TODO
            t.printStackTrace();
            execute(this);
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

            selector.wakeup();
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

            synchronized (selectorLock) {
                selector.wakeup();
                for (SelectionKey key : selector.keys())
                    forceClose((AsyncChannelImpl) key.attachment());
            }

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
}
