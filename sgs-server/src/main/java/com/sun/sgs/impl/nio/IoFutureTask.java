/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.nio;

import com.sun.sgs.nio.channels.IoFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * An implementation of {@code IoFuture} that adds an object attachment to a
 * {@code FutureTask}.
 *
 * @param <R> the result type
 * @param <A> the attachment type
 */
public class IoFutureTask<R, A> extends FutureTask<R>
    implements IoFuture<R, A>
{
    /**
     * The attachment for this {@code IoFuture}.  This field is
     * {@code volatile} to match the attachment implementation in
     * {@link java.nio.channels.SelectionKey}.
     */
    private volatile A attachment;
    
    /**
     * Creates an instance of this class for running the specified {@code
     * Callable} and including the specified attachment.
     *
     * @param	callable the callable task
     * @param	attachment the attachment; may be {@code null}
     */
    public IoFutureTask(Callable<R> callable, A attachment) {
	super(callable);
	this.attachment = attachment;
    }

    /**
     * Creates an instance of this class for running the specified {@code
     * Callable} and including the specified attachment.
     *
     * @param	<R> the result type
     * @param	<A> the attachment type
     * @param	callable the callable task
     * @param	attachment the attachment; may be {@code null}
     * @return	a new {@code IoFuture}
     */
    public static <R, A> IoFuture newInstance(
	Callable<R> callable, A attachment)
    {
	return new IoFutureTask<R, A>(callable, attachment);
    }



    /* -- Implement IoFuture -- */

    /** {@inheritDoc} */
    public R getNow() throws ExecutionException {
        if (!isDone()) {
            throw new IllegalStateException("The computation is not done");
	}
        /*
         * The future is done, so a result should be available immediately.
         * The following idiom appears in "Java Concurrency in Practice" to
         * handle the case where this thread has been interrupted, but a result
         * must be obtained regardless.
         */
        boolean wasInterrupted = false;
        try {
            while (true) {
                try {
                    return get();
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                    /* Fall through and retry */
                }
            }
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** {@inheritDoc} */
    public A attachment() {
        return attachment;
    }

    /** {@inheritDoc} */
    public A attach(A newAttachment) {
        A previousAttachment = attachment;
        attachment = newAttachment;
        return previousAttachment;
    }
}
