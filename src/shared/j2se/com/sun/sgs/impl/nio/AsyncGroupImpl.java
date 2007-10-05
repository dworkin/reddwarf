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
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

/**
 * Common base implementation of {@link AsynchronousChannelGroup}.
 */
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

    /**
     * Registers the given channel with this group, returning an
     * {@link AsyncKey} that can be used to invoke asynchronous IO
     * operations.  If this group is shutdown, this method will throw
     * {@link ShutdownChannelGroupException} and close the channel.
     * 
     * @param ch the underlying channel for IO operations
     * @return an {@code AsyncKey} that supports asynchronous operations
     *         on the underlying channel
     * 
     * @throws IOException if an I/O error occurs
     * @throws ShutdownChannelGroupException if this group is shutdown
     */
    abstract AsyncKey
    register(SelectableChannel ch) throws IOException;

    /**
     * Executes the given completion handler using this group's executor.
     * Passes the handler an {@code IoFuture} constructed from the given
     * future and attachment object.
     * 
     * @param <R> the result type
     * @param <A> the attachment type
     * @param handler the completion handler, or {@code null} to do nothing
     * @param attachment the attachment, or {@code null}
     * @param future the result
     */
    <R, A> void
    executeCompletion(CompletionHandler<R, A> handler,
                      A attachment,
                      Future<R> future)
    {
        assert future.isDone();

        if (handler == null)
            return;

        executor.execute(
            new CompletionRunner<R, A>(handler, attachment, future));
    }

    /**
     * A Runnable that invokes its completion handler with the given
     * {@code IoFuture} result.
     * 
     * @param <R> the result type
     * @param <A> the attachment type
     */
    class CompletionRunner<R, A> implements Runnable {

        /** The completion handler to invoke. */
        private final CompletionHandler<R, A> handler;

        /** The result to pass to the completion hander. */
        private final IoFuture<R, A> result;

        /**
         * Creates a new instance to run the given completion handler.
         * 
         * @param handler the completion handler to invoke
         * @param attachment the attachment for the future
         * @param future the future to pass to the completion handler
         */
        CompletionRunner(CompletionHandler<R, A> handler,
                         A attachment,
                         Future<R> future)
        {
            this.handler = handler;
            this.result = AttachedFuture.wrap(future, attachment);
        }

        /** {@inheritDoc} */
        public void run() {
            try {
                handler.completed(result);
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
