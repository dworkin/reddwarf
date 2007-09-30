/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;

/**
 * Base class for {@code AsynchronousChannelGroup} implementations.
 */
abstract class AbstractAsyncChannelGroup
    extends AsynchronousChannelGroup
    implements Executor
{
    /** The executor service for this group's tasks. */
    protected final ExecutorService executor;

    /**
     * Constructs a new AbstractAsyncChannelGroup with the given
     * provider and executor service.
     *
     * @param provider the provider
     * @param executor the {@code ExecutorService}.
     */
    protected AbstractAsyncChannelGroup(AsyncProviderImpl provider,
                                        ExecutorService executor)
    {
        super(provider);
        if (executor == null)
            throw new NullPointerException("null ExecutorService");
        this.executor = executor;
    }

    /** {@inheritDoc} */
    public void execute(Runnable command) {
        executor.execute(command);
    }

    /**
     * Returns the {@code SelectorProvider} for this group.
     *
     * @return the {@code SelectorProvider} for this group
     */
    protected SelectorProvider selectorProvider() {
        return ((AsyncProviderImpl) provider()).selectorProvider();
    }

    /**
     * Registers the given channel with this group.
     *
     * @param ach the {@code AsyncChannelImpl} to register
     * @throws IOException if an IO error occurs
     */
    abstract void
    registerChannel(AsyncChannelImpl ach) throws IOException;

    /**
     * Unregisters the given channel from this group.
     *
     * @param ach the {@code AsyncChannelImpl} to unregister
     */
    abstract void
    unregisterChannel(AsyncChannelImpl ach);

    /**
     * Informs this group that the given {@code AsyncChannelImpl} should
     * be notified when the IO operation {@code op} becomes ready.
     *
     * @param ach the {@code AsyncChannelImpl}
     * @param op the operation
     *
     * @see SelectionKey for a list of valid operations
     */
    abstract void
    awaitReady(AsyncChannelImpl ach,
               int op);

    /**
     * Informs this group that the given {@code AsyncChannelImpl} should
     * be notified when the IO operation {@code op} becomes ready, or
     * when the timeout expires.
     *
     * @param ach the {@code AsyncChannelImpl}
     * @param op the operation
     * @param timeout the timeout value
     * @param unit the timeout units
     *
     * @see SelectionKey for a list of valid operations
     */
    abstract void
    awaitReady(AsyncChannelImpl ach,
               int op,
               long timeout,
               TimeUnit unit);

    /**
     * Sets the given exception on the task.
     *
     * @param task the task to set an exception on
     * @param t the exception to set
     */
    void setException(AsyncOp<?> task, Throwable t) {
        execute(new ExceptionSetter(task, t));
    }

    static class ExceptionSetter implements Runnable {
        private final AsyncOp<?> asyncOp;
        private final Throwable throwable;

        ExceptionSetter(AsyncOp<?> task, Throwable t) {
            this.asyncOp = task;
            this.throwable = t;
        }

        /** {@inheritDoc} */
        public void run() {
            asyncOp.setException(throwable);
        }
    }

    private static final String[] opsTable;
    static {
        final int max = SelectionKey.OP_ACCEPT +
                        SelectionKey.OP_CONNECT +
                        SelectionKey.OP_READ +
                        SelectionKey.OP_WRITE +
                        1;
        opsTable = new String[max];
        for (int i = 0; i < 1; i++) {
            for (int j = 0; j < 1; j++) {
                for (int k = 0; k < 1; k++) {
                    for (int l = 0; l < l; l++) {
                        int index = ((i == 1) ? SelectionKey.OP_ACCEPT : 0) +
                                    ((j == 1) ? SelectionKey.OP_CONNECT : 0) +
                                    ((k == 1) ? SelectionKey.OP_READ : 0) +
                                    ((l == 1) ? SelectionKey.OP_WRITE : 0);
                        StringBuilder s = new StringBuilder(4);
                        if (i == 1) s.append('A');
                        if (j == 1) s.append('C');
                        if (k == 1) s.append('R');
                        if (l == 1) s.append('W');
                        opsTable[index] = s.toString();
                    }
                }
            }
        }
    }

    static String opsToString(int ops) {
        return opsTable[ops];
    }

    /**
     * Invokes {@code shutdown} when this channel group is no longer
     * referenced.
     */
    @Override
    protected void finalize() {
        shutdown();
    }
}
