/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.threaded;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

class AsyncIoTask<R, A>
    implements RunnableFuture<R>, IoFuture<R, A>
{
    private final InnerTask<R, ?> innerTask;
    private A attachment;

    static <R, A> AsyncIoTask<R, A>
        create(final Callable<R> callable,
               A attachment,
               CompletionHandler<R, ? super A> handler)
    {
        return new AsyncIoTask<R, A>(callable, attachment, handler);
    }

    AsyncIoTask(Callable<R> callable,
                A attachment,
                CompletionHandler<R, ? super A> handler) {
        this.attachment = attachment;
        innerTask = createInnerTask(callable, attachment, handler);
    }

    private static <R, X> InnerTask<R, X> createInnerTask(
            Callable<R> callable,
            X innerAttachment, 
            CompletionHandler<R, X> handler)
    {
        return new InnerTask<R, X>(callable, innerAttachment, handler);
    }

    static final class InnerTask<R, X>
        extends FutureTask<R>
        implements IoFuture<R, X>
    {
        private X innerAttachment;
        private CompletionHandler<R, X> handler;

        InnerTask(Callable<R> callable,
                  X attachment,
                  CompletionHandler<R, X> handler)
        {
            super(callable);
            innerAttachment = (handler == null) ? null : attachment;
            this.handler = handler;
        }

        @Override
        protected void done() {
            if (handler != null)
                handler.completed(this);
        }

        public X attach(X ob) {
            return innerAttachment = ob;
        }

        public X attachment() {
            return innerAttachment;
        }

        public R getNow() throws ExecutionException {
            if (! isDone())
                throw new IllegalStateException("not done");

            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        return get();
                    } catch (InterruptedException e) {
                        interrupted = true;
                        // fall through and retry
                    }
                }
            } finally {
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
        }
    }

    public A attach(A ob) {
        return attachment = ob;
    }

    public A attachment() {
        return attachment;
    }

    // Delegate to InnerTask

    public R getNow() throws ExecutionException {
        return innerTask.getNow();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return innerTask.cancel(mayInterruptIfRunning);
    }

    public R get() throws InterruptedException, ExecutionException {
        return innerTask.get();
    }

    public R get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException
    {
        return innerTask.get(timeout, unit);
    }

    public boolean isCancelled() {
        return innerTask.isCancelled();
    }

    public boolean isDone() {
        return innerTask.isDone();
    }

    public void run() {
        innerTask.run();
    }
}
