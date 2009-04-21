/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

        if (executor == null) {
            throw new NullPointerException("null executor");
        }

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
     * Returns a runnable that will invoke the given completion handler.
     * Passes the handler an {@code IoFuture} constructed from the given
     * future and attachment object.
     * 
     * @param <R> the result type
     * @param <A> the attachment type
     * @param handler the completion handler
     * @param attachment the attachment, or {@code null}
     * @param future the result
     * @return the completion runnable
     */
    <R, A> Runnable
    completionRunner(CompletionHandler<R, A> handler,
                     A attachment,
                     Future<R> future)
    {
        assert future.isDone();

        return new CompletionRunner<R, A>(handler, attachment, future);
    }


    /**
     * Invokes the uncaught exception handler of the current thread, if
     * one is present.  Does not re-throw the exception.
     * 
     * @param <T> the type of the exception
     * @param exception the exception
     */
    private <T extends Throwable> void uncaught(T exception) {
        try {
            final Thread.UncaughtExceptionHandler ueh =  uncaughtHandler;

            if (ueh != null) {
                ueh.uncaughtException(Thread.currentThread(), exception);
            }

        } catch (Throwable ignore) {
            // Ignore all throwables here, even Errors, as specified
            // by Thread#UncaughtExceptionHandler#uncaughtException
        }
    }

    /**
     * A Runnable that invokes its completion handler with the given
     * {@code IoFuture} result.
     * 
     * @param <R> the result type
     * @param <A> the attachment type
     */
    private class CompletionRunner<R, A> implements Runnable {

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
                uncaught(e);
            } catch (Error e) {
                uncaught(e);
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
