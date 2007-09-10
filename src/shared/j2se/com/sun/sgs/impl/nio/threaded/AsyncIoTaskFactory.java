/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.threaded;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

class AsyncIoTaskFactory {

    final AsyncChannelGroupImpl group;
    final AtomicBoolean pending = new AtomicBoolean();

    public AsyncIoTaskFactory(AsyncChannelGroupImpl group) {
        this.group = group;
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
            IoFuture<R, A> future =
                group.submit(callable, attachment, wrapHandler(handler));
            success = true;
            return future;
        } finally {
            if (! success)
                pending.set(false);
        }
    }

    private <R, A> InnerHandler<R, A> wrapHandler(
            CompletionHandler<R, A> handler)
    {
        return new InnerHandler<R, A>(handler);
    }

    final class InnerHandler<R, A> implements CompletionHandler<R, A> {
        private final CompletionHandler<R, A> handler;

        InnerHandler(CompletionHandler<R, A> handler) {
            this.handler = handler;
        }

        public void completed(IoFuture<R, A> result) {
            pending.set(false);
            if (handler != null)
                handler.completed(result);
        }
    }
}
