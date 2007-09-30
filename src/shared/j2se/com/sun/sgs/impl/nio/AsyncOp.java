/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import com.sun.sgs.nio.channels.CompletionHandler;

/**
 * Represents an asynchronous operation that can be waited on, and which
 * can optionally invoke a completion handler when the operation is finished.
 * 
 * @param <R> the result type
 */
class AsyncOp<R>
    extends FutureTask<R>
{
    /**
     * The task to perform on completion of this operation, or null.
     */
    private final Runnable completionRunner;

    static <R, A> AsyncOp<R>
    create(A attachment,
           CompletionHandler<R, A> handler,
           Callable<R> callable)
    {
        return new AsyncOp<R>(attachment, handler, callable);
    }

    <A> AsyncOp(
            A attachment,
            CompletionHandler<R, A> handler,
            Callable<R> callable)
    {
        super(callable);
        completionRunner =
            (handler == null) ? null : getCompletion(handler, attachment, this);
    }

    static <R, A> Runnable
    getCompletion(final CompletionHandler<R, A> handler,
                  final A attachment,
                  final Future<R> future)
    {
        return new Runnable() {
            public void run() {
                handler.completed(AttachedFuture.wrap(future, attachment));
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void done() {
        if (completionRunner != null)
            completionRunner.run();
    }

    /** {@inheritDoc} */
    @Override
    // Overridden to make public
    public void setException(Throwable t) {
        super.setException(t);
    }
}
