/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

class AsyncIoTaskFactory {

    private final AbstractAsyncChannelGroup channelGroup;
    final AtomicBoolean pending = new AtomicBoolean();

    public AsyncIoTaskFactory(AbstractAsyncChannelGroup group) {
        this.channelGroup = group;
    }

    /**
     * What to do if there is already an operation pending.
     * The default implementation does nothing.
     */
    protected void alreadyPendingPolicy() { }

    public boolean isPending() {
        return pending.get();
    }

    public <R, A> IoFuture<R, A>
        submit(A attachment,
               CompletionHandler<R, ? super A> handler,
               Callable<R> callable)
    {
        if (! pending.compareAndSet(false, true))
            alreadyPendingPolicy();

        boolean success = false;
        try {
            FutureTask<R> task = new AsyncIoTask<R>(callable, attachment, handler);
            channelGroup.executor().execute(task);
            success = true;
            return new IoFutureBase<R, A>(task, attachment);
        } finally {
            if (! success)
                pending.set(false);
        }
    }

    static class IoFutureBase<R, A> implements IoFuture<R, A> {
        private final Future<R> future;
        private A attachment;

        IoFutureBase(Future<R> future, A attachment) {
            this.future = future;
            this.attachment = attachment;
        }

        public A attach(A ob) {
            return attachment = ob;
        }

        public A attachment() {
            return attachment;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
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

        public R get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }

        public boolean isCancelled() {
            return future.isCancelled();
        }

        public boolean isDone() {
            return future.isDone();
        }
    }

    final class AsyncIoTask<R> extends FutureTask<R> {
        private final Runnable doneRunner;

        <A> AsyncIoTask(Callable<R> callable, A attachment,
            CompletionHandler<R, ? super A> handler) {
            super(callable);
            doneRunner = (handler == null) ? null : getDoneRunner(this, attachment, handler);
        }
        
        @Override
        protected void done() {
            if (doneRunner != null)
                doneRunner.run();
        }
    }
    
    <R, A> DoneRunner<R, A> getDoneRunner(
            Future<R> future,
            A attachment,
            CompletionHandler<R, A> handler)
    {
        return new DoneRunner<R, A>(future, attachment, handler);
    }

    final class DoneRunner<R, A>
        extends IoFutureBase<R, A>
        implements Runnable
    {
        private final CompletionHandler<R, A> handler;

        DoneRunner(Future<R> future, A attachment, CompletionHandler<R, A> handler) {
            super(future, attachment);
            this.handler = handler;
        }

        public void run() {
            pending.set(false);
            handler.completed(this);
        }
    }
}
