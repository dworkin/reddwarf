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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AbortedByTimeoutException;
import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;
import com.sun.sgs.nio.channels.WritePendingException;

class ReactiveChannelGroup
    extends AbstractAsyncChannelGroup
{
    static final Logger log =
        Logger.getLogger(ReactiveChannelGroup.class.getName());

    public static final String SELECTORS_PROPERTY =
        "com.sun.sgs.nio.async.reactive.selectors";
    public static final String DEFAULT_SELECTORS = "1";

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
    private final Object mainLock = new Object();

    final CountDownLatch termination;

    static final class SelectorHolder {
        /*
         * "this" also acts as the selector guard.
         * May be locked *after* mainLock, but not before.
         */

        final Selector selector;
        final DelayQueue<TimeoutHandler> timeouts =
            new DelayQueue<TimeoutHandler>();

        SelectorHolder(Selector selector) {
            this.selector = selector;
        }
    }

    final int numSelectors;
    final List<SelectorHolder> selectors;

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

    ReactiveChannelGroup(AsyncProviderImpl provider, ExecutorService executor)
        throws IOException
    {
        this(provider, executor, 0);
    }

    // if numSelectors == 0, choose from a property
    ReactiveChannelGroup(AsyncProviderImpl provider,
                         ExecutorService executor,
                         int numSelectors)
        throws IOException
    {
        super(provider, executor);

        if (numSelectors == 0) {
            numSelectors = Integer.valueOf(System.getProperty(
                SELECTORS_PROPERTY, DEFAULT_SELECTORS));
        }

        if (numSelectors <= 0)
            throw new IllegalArgumentException(
                "Selector count must be positive");

        this.numSelectors = numSelectors;
        this.termination = new CountDownLatch(numSelectors);

        ArrayList<SelectorHolder> sels =
            new ArrayList<SelectorHolder>(numSelectors);

        for (int i = 0; i < numSelectors; ++i) {
            sels.add(new SelectorHolder(selectorProvider().openSelector()));
        }

        selectors = Collections.unmodifiableList(sels);

        for (SelectorHolder selector : selectors) {
            execute(new LoopingWorkerStrategy(selector));
        }
    }

    SelectorHolder getSelectorHolder(AsyncChannelImpl ach) {
        int index = Math.abs(ach.hashCode()) % numSelectors;
        return selectors.get(index);
    }

    @Override
    void registerChannel(AsyncChannelImpl ach) throws IOException {
        synchronized (mainLock) {
            final SelectableChannel ch = ach.channel();
            if (isShutdown()) {
                try {
                    ch.close();
                } catch (IOException ignore) { }
                throw new ShutdownChannelGroupException();
            }
            ch.configureBlocking(false);
            SelectorHolder h = getSelectorHolder(ach);
            synchronized (h) {
                Selector selector = h.selector;
                selector.wakeup();
                ch.register(selector, 0, ach);
            }
        }
    }

    @Override
    void unregisterChannel(AsyncChannelImpl ach) {
        SelectorHolder h = getSelectorHolder(ach);
        synchronized (h) {
            Selector selector = h.selector;
            selector.wakeup();
            SelectionKey key = ach.channel().keyFor(selector);
            if (key != null)
                key.cancel();
        }
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
        SelectorHolder h = getSelectorHolder(ach);
        synchronized (h) {
            Selector selector = h.selector;
            selector.wakeup();
            SelectionKey key = ach.channel().keyFor(selector);
            if (key == null || (! key.isValid()))
                throw new ClosedAsynchronousChannelException();
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

    static int getSelectorTimeout(DelayQueue<? extends Delayed> queue) {
        final Delayed t = queue.peek();
        return (t == null) ? 0 : (int) t.getDelay(TimeUnit.MILLISECONDS);
    }

    protected void doWork(SelectorHolder h) throws IOException {
        Object selectorLock = h;
        Selector selector = h.selector;
        DelayQueue<TimeoutHandler> timeouts = h.timeouts;

        int rc = 0;
        log.log(Level.FINER, "preselect");
        // Obtain and release the guard to allow other tasks to run
        // after waking the selector.
        synchronized (selectorLock) {
            // FIXME experimenting with a selectNow to clear
            // spurious wakeups
            rc = selector.selectNow();
            log.log(Level.FINER, "preselect returned {0}", rc);
        }

        int numKeys = selector.keys().size();

        if (rc == 0) {
            log.log(Level.FINER, "select {0}", numKeys);            
            rc = selector.select(getSelectorTimeout(timeouts));
            log.log(Level.FINEST, "selected {0}", rc);
        }

        int state = runState;
        if (state != RUNNING) {
            synchronized (mainLock) {
                if (! selector.isOpen())
                    return;
                if (selector.keys().isEmpty()) {
                    try {
                        selector.close();
                    } catch (IOException ignore) { }
                    termination.countDown();
                    return;
                }
            }
        }

        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            keys.remove();

            AsyncChannelImpl ach;
            int readyOps;
            synchronized (selectorLock) {
                if (! key.isValid())
                    continue;
                readyOps = key.readyOps();
                key.interestOps(key.interestOps() & (~ readyOps));
                ach = (AsyncChannelImpl) key.attachment();
            }
            ach.selected(readyOps);
        }

        if (timeouts.peek() != null) {
            List<TimeoutHandler> expiredHandlers = new ArrayList<TimeoutHandler>();
            timeouts.drainTo(expiredHandlers);

            for (TimeoutHandler expired : expiredHandlers)
                expired.run();
        }
    }

    abstract class WorkerStrategy implements Runnable {
        protected final SelectorHolder selectorHolder;
        protected WorkerStrategy(SelectorHolder h) {
            this.selectorHolder = h;
        }
    }
    class LoopingWorkerStrategy extends WorkerStrategy {
        LoopingWorkerStrategy(SelectorHolder h) {
            super(h);
        }

        public void run() {
            try {
                while (selectorHolder.selector.isOpen()) {
                    doWork(selectorHolder);
                }
            } catch (Exception e) {
                // TODO
                e.printStackTrace();
                if (selectorHolder.selector.isOpen())
                    execute(this);
            }
        }
    }

    class RequeuingWorkerStrategy extends WorkerStrategy {
        RequeuingWorkerStrategy(SelectorHolder h) {
            super(h);
        }

        public void run() {
            try {
                doWork(selectorHolder);
            } catch (Exception e) {
                // TODO
                e.printStackTrace();
            }
            if (selectorHolder.selector.isOpen())
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
        return termination.await(timeout, unit);
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
        synchronized (mainLock) {
            int state = runState;
            if (state < SHUTDOWN)
                runState = SHUTDOWN;

            for (SelectorHolder h : selectors) {
                synchronized (h) {
                    h.selector.wakeup();
                }
            }

            return this;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactiveChannelGroup shutdownNow() throws IOException
    {
        synchronized (mainLock) {
            int state = runState;
            if (state < STOP)
                runState = STOP;

            for (SelectorHolder h : selectors) {
                synchronized (h) {
                    Selector selector = h.selector;
                    selector.wakeup();
                    for (SelectionKey key : selector.keys())
                        forceClose((AsyncChannelImpl) key.attachment());
                }
            }

            return this;
        }
    }

    private static void forceClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignore) { }
    }
}
