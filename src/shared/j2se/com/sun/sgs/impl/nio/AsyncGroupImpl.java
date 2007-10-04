/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.channels.SelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.CompletionHandler;

abstract class AsyncGroupImpl
    extends AsynchronousChannelGroup
{
    /** The executor service for this group's tasks. */
    protected final ExecutorService executor;

    /**
     * The UncaughtExceptionHandler for this group, or {@code null}.
     *
     * Accessed by AsyncProviderImpl if this is the default group.
     */
    volatile UncaughtExceptionHandler uncaughtHandler = null;

    /**
     * Constructs a new instance of this class.
     * 
     * @param provider the provider
     * @param executor the executor
     */
    protected
    AsyncGroupImpl(AsyncProviderImpl provider, ExecutorService executor)
    {
        super(provider);

        if (executor == null)
            throw new NullPointerException("null executor");

        this.executor = executor;
    }

    abstract <T extends SelectableChannel> AsyncKey<T>
    register(T ch) throws IOException;

    <R, A> void
    executeCompletion(CompletionHandler<R, A> handler,
                      A attachment,
                      Future<R> future)
    {
        if (handler == null)
            return;
        executor.execute(
            new CompletionRunner<R, A>(handler, attachment, future));
    }

    class CompletionRunner<R, A> implements Runnable {
        private final CompletionHandler<R, A> handler;
        private final A attachment;
        private final Future<R> future;

        CompletionRunner(CompletionHandler<R, A> handler,
                         A attachment,
                         Future<R> future)
        {
            this.handler = handler;
            this.attachment = attachment;
            this.future = future;
        }

        /** {@inheritDoc} */
        public void run() {
            try {
                handler.completed(AttachedFuture.wrap(future, attachment));
            } catch (RuntimeException e) {
                final UncaughtExceptionHandler eh = uncaughtHandler;
                if (eh != null)
                    eh.uncaughtException(Thread.currentThread(), e);
                throw e;
            } catch (Error e) {
                final UncaughtExceptionHandler eh = uncaughtHandler;
                if (eh != null)
                    eh.uncaughtException(Thread.currentThread(), e);
                throw e;
            }
        }
    }
    /**
     * Returns the {@code SelectorProvider} for this group.
     *
     * @return the {@code SelectorProvider} for this group
     */
    SelectorProvider selectorProvider() {
        return ((AsyncProviderImpl) provider()).selectorProvider();
    }

    /**
     * Returns the {@code ExecutorService} for this group.
     *
     * @return the {@code ExecutorService} for this group
     */
    ExecutorService executor() {
        return executor;
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
