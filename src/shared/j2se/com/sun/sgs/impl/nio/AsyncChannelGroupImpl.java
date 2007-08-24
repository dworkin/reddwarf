/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
    final Selector selector;

    volatile int runState;
    static final int RUNNING    = 0;
    static final int SHUTDOWN   = 1;
    static final int STOP       = 2;
    static final int TERMINATED = 3;

    private final ReentrantLock mainLock = new ReentrantLock();
    private final Condition termination = mainLock.newCondition();

    private final Condition noTasksWaiting = mainLock.newCondition();
    int numTasksWaiting = 0;

    static final int MAX_DISPATCHES_PER_WORK_LOOP = 1;

    AsyncChannelGroupImpl(DefaultAsynchronousChannelProvider provider,
                          ExecutorService executor) 
        throws IOException
    {
        super(provider);
        if (executor == null)
            throw new NullPointerException("null ExecutorService");
        this.executor = executor;
        this.selector = Selector.open();
        this.executor.submit(new Worker());
    }

    AsyncChannelGroupImpl checkShutdown() {
        if (isShutdown())
            throw new ShutdownChannelGroupException();
        return this;
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

            selector.wakeup();
            return this;
        } finally {
            mainLock.unlock();
        }
    }

    AsyncChannelHandler getHandler(SelectionKey key) {
        assert key.selector() == selector;
        return (AsyncChannelHandler) key.attachment();
    }

    SelectionKey getKey(AsyncChannelHandler handler) {
        return handler.getSelectableChannel().keyFor(selector);
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

            selector.wakeup();
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

    void register(AsyncChannelHandler handler) throws IOException {
        checkShutdown();
        incrementSelectorTasks();
        try {
            checkShutdown();
            handler.getSelectableChannel().register(selector, 0, handler);
        } finally {
            decrementSelectorTasks();
        }
    }

    void addOps(AsyncChannelHandler handler, int ops) {
        incrementSelectorTasks();
        try {
            SelectionKey key = getKey(handler);
            key.interestOps(key.interestOps() | ops);
        } finally {
            decrementSelectorTasks();
        }
    }

    void cancelOps(AsyncChannelHandler handler, int ops) {
        incrementSelectorTasks();
        try {
            SelectionKey key = getKey(handler);
            key.interestOps(key.interestOps() & (~ ops));
        } finally {
            decrementSelectorTasks();
        }
    }

    private void incrementSelectorTasks() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (++numTasksWaiting == 1) {
                selector.wakeup();
            }
        } finally {
            mainLock.unlock();
        }
    }

    private void decrementSelectorTasks() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (--numTasksWaiting == 0) {
                noTasksWaiting.signal();
            }
        } finally {
            mainLock.unlock();
        }
    }

    void awaitSelectorTasks() throws InterruptedException {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (numTasksWaiting == 0)
                    break;
                noTasksWaiting.await();
            }
        } finally {
            mainLock.unlock();
        }
    }

    final class Worker 
        implements Runnable
    {
        public void run() {
            try {
                awaitSelectorTasks();
            } catch (InterruptedException e) {
                // TODO
            }
            doSelect();
            checkShutdown();
            dispatchEvents();

            // Repeat
            executor.submit(this);
        }
        
        private void doSelect() {

            // Already have pending events from a previous work loop?
            if (selector.selectedKeys().isEmpty()) {
                // Wait for events
                try {
                    selector.select();
                } catch (IOException e) {
                    // TODO close the selector? something else? -JM
                }
            }
        }

        private void checkShutdown() {
            if (runState == RUNNING)
                return;

            for (SelectionKey key : selector.keys()) {
                try {
                    getHandler(key).close();
                } catch (IOException ignore) { }
            }

            // selector.close();
        }

        private void dispatchEvents() {
            // Handle events
            Iterator<SelectionKey> selectedKeyIter =
                selector.selectedKeys().iterator();
            int allowedDispatches = MAX_DISPATCHES_PER_WORK_LOOP;
            while (selectedKeyIter.hasNext() &&
                    (allowedDispatches-- > 0) &&
                    (! Thread.currentThread().isInterrupted()))
            {
                SelectionKey key = selectedKeyIter.next();
                int readyOps = key.readyOps();
                key.interestOps(key.interestOps() & (~ readyOps));
                selectedKeyIter.remove();

                try {
                    getHandler(key).channelSelected(readyOps);
                } catch (Exception e) {
                   // closeChannel(key.channel()); // TODO remove?
                } finally {
                    if (! key.isValid()) {
                        //closeChannel(key.channel()); // TODO remove?
                    }
                }
            }
        }
    }
}
