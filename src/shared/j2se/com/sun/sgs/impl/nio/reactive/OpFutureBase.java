/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.reactive;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

class OpFutureBase<R, B, A extends B>
    implements IoFuture<R, A>, RunnableFuture<R>
{
    static <R, B, A extends B> OpFutureBase<R, B, A>
        create(Runnable runner, A attachment, CompletionHandler<R, B> handler)
    {
        return new OpFutureBase<R, B, A>(runner, attachment, handler);
    }

    private final Runnable runner;
    private A attachment1;
    private B attachment2;
    private final CompletionHandler<R, B> handler;

    protected OpFutureBase(Runnable runner,
            A attachment,
            CompletionHandler<R, B> handler)
    {
        this.runner = runner;
        this.attachment1 = attachment;
        this.attachment2 = attachment;
        this.handler = handler;
    }

    /**
     * {@inheritDoc}
     */
    public A attach(A ob) {
        return attachment1 = ob;
    }

    /**
     * {@inheritDoc}
     */
    public A attachment() {
        return attachment1;
    }

    /**
     * {@inheritDoc}
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        // TODO
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public R getNow() throws ExecutionException {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public R get() throws InterruptedException, ExecutionException {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isCancelled() {
        // TODO
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDone() {
        // TODO
        return false;
    }

    void notifyCompletionHandler() {
        handler.completed(new IoFuture<R, B>() {
            public B attach(B ob) { return attachment2 = ob; }
            public B attachment() { return attachment2; }

            public boolean cancel(boolean mayInterruptIfRunning) {
                return OpFutureBase.this.cancel(mayInterruptIfRunning);
            }

            public R getNow() throws ExecutionException {
                return OpFutureBase.this.getNow();
            }

            public R get() throws InterruptedException, ExecutionException {
                return OpFutureBase.this.get();
            }

            public R get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException
            {
                return OpFutureBase.this.get(timeout, unit);
            }

            public boolean isCancelled() {
                return OpFutureBase.this.isCancelled();
            }

            public boolean isDone() {
                return OpFutureBase.this.isDone();
            }
        });
    }

    public void run() {
        runner.run();
    }
}
