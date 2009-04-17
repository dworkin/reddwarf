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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.sgs.nio.channels.IoFuture;

/**
 * An implementation of {@link IoFuture} that adds an object attachment
 * to an arbitrary {@link java.util.concurrent.Future}.
 *
 * @param <R> the result type
 * @param <A> the attachment type
 */
public class AttachedFuture<R, A> implements IoFuture<R, A> {

    /**
     * The underlying {@code Future}.
     */
    private final Future<R> future;

    /**
     * The attachment for this {@code IoFuture}.  This field is
     * {@code volatile} to match the attachment implementation in
     * {@link java.nio.channels.SelectionKey}.
     */
    private volatile A attachment;

    /**
     * Creates an {@code AttachedFuture} from the given future
     * and attachment.  Provided as a static factory method so that
     * the template types can be inferred by the compiler.
     *
     * @param <R> the result type
     * @param <A> the attachment type
     * @param future the {@code Future} to wrap
     * @param attachment the initial attachment, or {@code null}
     * @return a new {@code AttachedFuture}
     */
    public static <R, A> AttachedFuture<R, A>
    wrap(Future<R> future, A attachment) {
        return new AttachedFuture<R, A>(future, attachment);
    }

    /**
     * Creates an {@code AttachedFuture} from the given future
     * and attachment.
     *
     * @param future the {@code Future} to wrap
     * @param attachment the initial attachment, or {@code null}
     */
    protected AttachedFuture(Future<R> future, A attachment) {
        this.future = future;
        this.attachment = attachment;
    }

    // Implement IoFuture

    /** {@inheritDoc} */
    public A attach(A ob) {
        A previous = attachment;
        attachment = ob;
        return previous;
    }

    /** {@inheritDoc} */
    public A attachment() {
        return attachment;
    }

    /** {@inheritDoc} */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    /** {@inheritDoc} */
    public R getNow() throws ExecutionException {
        if (!isDone()) {
            throw new IllegalStateException("not done");
        }

        /*
         * The Future is done, so a result should be available immediately.
         * The following idiom appears in "Java Concurrency in Practice" to
         * handle the case where this thread has been interrupted, but a
         * result must be obtained regardless.
         */

        boolean wasInterrupted = false;
        try {
            while (true) {
                try {
                    return get();
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                    // fall through and retry
                }
            }
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** {@inheritDoc} */
    public R get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    /** {@inheritDoc} */
    public R get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException
    {
        return future.get(timeout, unit);
    }

    /** {@inheritDoc} */
    public boolean isCancelled() {
        return future.isCancelled();
    }

    /** {@inheritDoc} */
    public boolean isDone() {
        return future.isDone();
    }
}
