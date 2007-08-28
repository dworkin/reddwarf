/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.tpc;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

abstract class AsyncIoTask<R, A>
    implements RunnableFuture<R>, IoFuture<R, A>
{
    private final InnerTask<?> innerTask;
    private A attachment;

    static <R, A> AsyncIoTask<R, A>
        create(final Callable<R> callable,
               A attachment,
               CompletionHandler<R, ? super A> handler,
               final AtomicBoolean pending)
    {
        return new AsyncIoTask<R, A>(attachment, handler) {
            @Override
            protected R performWork() throws Exception
            {
                return callable.call();
            }
            @Override
            protected void done() {
                if (pending != null)
                    pending.set(false);
            }
        };
    }

    AsyncIoTask(A attachment, CompletionHandler<R, ? super A> handler) {
        this.attachment = attachment;
        innerTask = createInnerTask(attachment, handler);
    }

    private <X> InnerTask<X>
        createInnerTask(X attachment, CompletionHandler<R, X> handler)
    {
        return new InnerTask<X>(attachment, handler);
    }

    final class InnerTask<X>
        extends FutureTask<R>
        implements IoFuture<R, X>
    {
        private X innerAttachment;
        private CompletionHandler<R, X> handler;

        InnerTask(X attachment, CompletionHandler<R, X> handler) {
            super(new Callable<R>() {
                public R call() throws Exception {
                    return performWork();
                }});
            this.innerAttachment = attachment;
            this.handler = handler;
        }

        @Override
        protected void done() {
            try {
                AsyncIoTask.this.done();
            } finally {
                handler.completed(this);
            }
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

    protected abstract R performWork() throws Exception;

    /**
     * @see FutureTask#done
     */
    protected void done() { }

    public A attach(A ob) {
        return attachment = ob;
    }

    public A attachment() {
        return attachment;
    }

    public R getNow() throws ExecutionException {
        return innerTask.getNow();
    }

    // Delegate to FutureTask

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
