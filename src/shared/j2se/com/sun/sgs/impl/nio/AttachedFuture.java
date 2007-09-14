/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.sgs.nio.channels.IoFuture;

public class AttachedFuture<R, A> implements IoFuture<R, A> {
    private final Future<R> future;
    private volatile A attachment;

    public static <R, A> AttachedFuture<R, A>
    wrap(Future<R> future, A attachment) {
        return new AttachedFuture<R, A>(future, attachment);
    }

    public AttachedFuture(Future<R> future, A attachment) {
        this.future = future;
        this.attachment = attachment;
    }

    public A attach(A ob) {
        A previous = attachment;
        attachment = ob;
        return previous;
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
            if (wasInterrupted)
                Thread.currentThread().interrupt();
        }
    }

    public R get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    public R get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException
    {
        return future.get(timeout, unit);
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }

    public boolean isDone() {
        return future.isDone();
    }
}
